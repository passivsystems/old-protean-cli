(ns protean.cli
  "A basic command line interface for Protean."
  (:require [clojure.string :as stg]
            [clojure.edn :as edn]
            [clojure.java.io :refer [file]]
  	        [clojure.tools.cli :refer [parse-opts]]
            [ring.util.codec :as cod]
  	        [clj-http.client :as clt]
            [cheshire.core :as jsn]
            [protean.transformations.analysis :as pta]
            [protean.transformations.curly :as txc])
  (:import java.net.URI)
  (:gen-class))

(defmacro get-version []
  (System/getProperty "protean-cli.version"))

(defn- codices->silk [f n d]
  (let [codices (edn/read-string (slurp f))
        locs {"locs" (if n (vector n) n)}
        an (pta/analysis-> "localhost" 8080 codices locs)]
    (doseq [e an]
      (let [path  (stg/replace (-> (URI. (:uri e)) (.getPath)) #"/" "-")
            id (str (name (:method e)) path)
            full (assoc e :id id :curl (cod/url-decode (txc/curly-> e)))]
        (spit (str d "/" id ".edn") (pr-str (update-in full [:method] name)))))))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 3001
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-H" "--host HOST" "Host name"
     :default "localhost"]
   ["-n" "--name NAME" "Project name"]
   ["-f" "--file FILE" "Project configuration file"]
   ["-d" "--directory DIRECTORY" "Documentation site"]
   ["-s" "--status-err STATUS-ERROR" "Error status code"]
   ["-l"  "--level LEVEL" "Error level (probability)"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Protean Command Line Interface"
        (get-version)
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  services               (List services)"
        "  service                -n myservice (List service)"
        "  service-usage          -n myservice (List curl statements to use API)"
        "  add-services           -f service-config-file.edn (Add services in a codex)"
        "  del-service            -n myservice (Delete a service)"
        "  add-service-error      -n myservice -s 500 (Add an error status code to a service)"
        "  set-service-error-prob -n myservice -l 10 (Set error probability)"
        "  del-service-errors     -n myservice (Delete error response codes)"
        "  doc                    -f codex -n name -d doc-site (Build API docs)"
        ""
        "Please refer to the manual page for more information."]
       (stg/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (stg/join \newline errors)))

(defn exit [status msg] (println msg) (System/exit status))

(defn projects [{:keys [host port]}]
	(let [rsp (clt/get (str "http://" host ":" port "/services"))]
    (println (:body rsp))))

(defn project [{:keys [host port name]}]
  (let [rsp (clt/get (str "http://" host ":" port "/services/" name))]
    (println (:body rsp))))

(defn project-usage [{:keys [host port name]}]
  (let [rsp (clt/get (str "http://" host ":" port "/services/" name "/usage"))]
    (doseq [j (jsn/parse-string (:body rsp))] (println j))))

(defn add-projects [{:keys [file host port]}]
  (let [rsp (clt/put (str "http://" host ":" port "/services")
              {:multipart [{:name "file"
                            :content (clojure.java.io/file file)}]})]
    (println (:body rsp))))

(defn delete-project [{:keys [host port name] :as options}]
  (let [rsp (clt/delete (str "http://" host ":" port "/services/" name)
                        {:throw-exceptions false})]
   (projects options)))

(defn add-project-error [{:keys [host port name status-err] :as options}]
  (let [rsp
    (clt/put (str "http://" host ":" port
                  "/services/" name "/errors/status/" status-err))]
    (project options)))

(defn set-project-error-prob [{:keys [host port name level] :as options}]
  (let [rsp
    (clt/put (str "http://" host ":" port
                  "/services/" name "/errors/probability/" level))]
    (project options)))

(defn del-project-errors [{:keys [host port name] :as options}]
  (let [rsp (clt/delete (str "http://" host ":" port "/services/" name "/errors"))]
    (project options)))

(defn doc [{:keys [host port file name directory] :as options}]
  (codices->silk file name directory))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors))
      (and (= (first arguments) "service")
           (not (:name options))) (exit 0 (usage summary))
      (and (= (first arguments) "add-services")
           (not (:file options))) (exit 0 (usage summary))
      (and (= (first arguments) "del-service")
           (not (:name options))) (exit 0 (usage summary))
      (and (= (first arguments) "add-service-error")
           (or (not (:name options))
               (not (:status-err options)))) (exit 0 (usage summary))
      (and (= (first arguments) "set-service-error-prob")
           (or (not (:name options))
               (not (:level options)))) (exit 0 (usage summary))
      (and (= (first arguments) "del-service-errors")
           (not (:name options))) (exit 0 (usage summary))
      (and (= (first arguments) "doc")
                (or (not (:name options))
                    (not (:file options))
                    (not (:directory options)))) (exit 0 (usage summary)))
    ;; Execute program with options
    (println "Protean Command Line Interface")
    (println (get-version))
    (println "\n")
    (case (first arguments)
      "services" (projects options)
      "service" (project options)
      "service-usage" (project-usage options)
      "add-services" (add-projects options)
      "del-service" (delete-project options)
      "add-service-error" (add-project-error options)
      "set-service-error-prob" (set-project-error-prob options)
      "del-service-errors" (del-project-errors options)
      "doc" (doc options)
      (exit 1 (usage summary)))))
