(ns protean.cli
  "A basic command line interface for Protean."
  (:require [clojure.string :as stg]
            [clojure.edn :as edn]
            [clojure.java.io :refer [file]]
  	        [clojure.tools.cli :refer [parse-opts]]
            [ring.util.codec :as cod]
  	        [clj-http.client :as clt]
            [protean.protocol.http :as pth]
            [protean.transformation.coerce :as ptc]
            [protean.transformation.analysis :as pta]
            [protean.transformation.curly :as txc]
            [protean.transformation.testy-cljhttp :as tc]
            [protean.test :as t])
  (:import java.net.URI)
  (:gen-class))

(defmacro get-version []
  (System/getProperty "protean-cli.version"))

(defn- cli-banner []
  (println "              _")
  (println " _ __ _ _ ___| |_ ___ __ _ _ _ ")
  (println "| '_ \\ '_/ _ \\  _/ -_) _` | ' \\")
  (println (str "| .__/_| \\___/\\__\\___\\__,_|_||_| " "v" (get-version)))
  (println "|_|                            "))

(defn- body [ctype body]
  (if-let [b body]
    (cond
      (= ctype pth/xml) (ptc/pretty-xml-> b)
      (= ctype pth/txt) b
      :else (ptc/js-> b))
    "N/A"))

(defn- codices->silk [f n d]
  (let [codices (edn/read-string (slurp f))
        locs {"locs" (if n (vector n) n)}
        an (pta/analysis-> "host" 1234 codices locs)]
    (doseq [e an]
      (let [uri-path (-> (URI. (:uri e)) (.getPath))
            path  (stg/replace uri-path #"/" "-")
            id (str (name (:method e)) path)
            body (body (get-in e [:codex :content-type]) (get-in e [:codex :body]))
            full (assoc e :id id :path (subs uri-path 1) :curl (cod/url-decode (txc/curly-> e)) :sample-response body)]
        (spit (str d "/" id ".edn") (pr-str (update-in full [:method] name)))))))

(defn- test-sim [h p f b]
  (println "Testing simulation")
  (let [codices (edn/read-string (slurp f))
        tests (tc/clj-httpify h p codices b)
        results (map #(t/test! %) tests)]
    (doseq [r results]
      (let [t (first r)
            s (:status (last r))]
        (println "Test : " t ", status : " s)))))

(defn- test-service [h p f b]
  (println "Testing service"))

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
   ["-b" "--body BODY" "JSON body"]
   ["-s" "--status-err STATUS-ERROR" "Error status code"]
   ["-l"  "--level LEVEL" "Error level (probability)"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (cli-banner)
  (->> [""
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
        "  test                   -f codex -b body (Test simulation or service)"
        ""
        "Please refer to the manual page for more information."]
       (stg/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (stg/join \newline errors)))

(defn exit [status msg] (println msg) (System/exit status))

(defn projects [{:keys [host port]}]
	(let [rsp (clt/get (str "http://" host ":" port "/services"))]
    (ptc/pretty-clj-> (:body rsp))))

(defn project [{:keys [host port name]}]
  (let [rsp (clt/get (str "http://" host ":" port "/services/" name))]
    (ptc/pretty-clj-> (:body rsp))))

(defn project-usage [{:keys [host port name]}]
  (let [rsp (clt/get (str "http://" host ":" port "/services/" name "/usage"))]
    (doseq [j (ptc/clj-> (:body rsp))] (println j))))

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

(defn test-locs [{:keys [host port file body] :as options}]
  (let [b (ptc/clj-> body) tp (b "port") th (b "host")
        p (or tp 3000) h (or th host)]
    (if (or tp th) (test-service h p file b) (test-sim h p file b))))

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
                    (not (:directory options)))) (exit 0 (usage summary))
      (and (= (first arguments) "test")
                (or (not (:file options))
                    (not (:body options)))) (exit 0 (usage summary)))
    ;; Execute program with options
    (cli-banner)
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
      "test" (test-locs options)
      (exit 1 (usage summary)))))
