(ns protean.cli
  (:require [clojure.string :as string]
            [clojure.java.io :refer [file]]
  	        [clojure.tools.cli :refer [parse-opts]]
  	        [clj-http.client :as clt]
            [cheshire.core :as jsn])
  (:use clojure.pprint)
  (:gen-class))

(defmacro get-version []
  (System/getProperty "protean-cli.version"))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 3001
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-H" "--host HOST" "Host name"
     :default "localhost"]
   ["-n" "--name NAME" "Project name"]
   ["-f" "--file FILE" "Project configuration file"]
   ["-s" "--status-err STATUS-ERROR" "Error status code"]
   ["-l"  "--level LEVEL" "Error level (probability)"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Protean"
        (get-version)
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  projects               (List projects)"
        "  project                -n myproject (List project)"
        "  project-usage          -n myproject (List curl statements to use API)"
        "  add-projects           -f project-config-file.edn (Add projects in a config file)"
        "  del-project            -n myproject (Delete a project)"
        "  add-project-error      -n myproject -s 500 (Add an error status code to a project)"
        "  set-project-error-prob -n myproject -l 10 (Set error probability)"
        "  del-project-errors     -n myproject (Delete error response codes)"
        ""
        "Please refer to the manual page for more information."]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg] (println msg) (System/exit status))

(defn projects [{:keys [host port]}]
	(let [rsp (clt/get (str "http://" host ":" port "/projects"))]
    (println (:body rsp))))

(defn project [{:keys [host port name]}]
  (let [rsp (clt/get (str "http://" host ":" port "/projects/" name))]
    (println (:body rsp))))

(defn project-usage [{:keys [host port name]}]
  (let [rsp (clt/get (str "http://" host ":" port "/projects/" name "/usage"))]
    (doseq [j (jsn/parse-string (:body rsp))] (println j))))

(defn add-projects [{:keys [file host port]}]
  (let [rsp (clt/put (str "http://" host ":" port "/projects")
              {:multipart [{:name "file"
                            :content (clojure.java.io/file file)}]})]
    (println (:body rsp))))

(defn delete-project [{:keys [host port name] :as options}]
  (let [rsp (clt/delete (str "http://" host ":" port "/projects/" name)
                        {:throw-exceptions false})]
   (projects options)))

(defn add-project-error [{:keys [host port name status-err] :as options}]
  (let [rsp
    (clt/put (str "http://" host ":" port
                  "/projects/" name "/errors/status/" status-err))]
    (project options)))

(defn set-project-error-prob [{:keys [host port name level] :as options}]
  (let [rsp
    (clt/put (str "http://" host ":" port
                  "/projects/" name "/errors/probability/" level))]
    (project options)))

(defn del-project-errors [{:keys [host port name] :as options}]
  (let [rsp (clt/delete (str "http://" host ":" port "/projects/" name "/errors"))]
    (project options)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors))
      (and (= (first arguments) "project")
           (not (:name options))) (exit 0 (usage summary))
      (and (= (first arguments) "add-projects")
           (not (:file options))) (exit 0 (usage summary))
      (and (= (first arguments) "del-project")
           (not (:name options))) (exit 0 (usage summary))
      (and (= (first arguments) "add-project-error")
           (or (not (:name options))
               (not (:status-err options)))) (exit 0 (usage summary))
      (and (= (first arguments) "set-project-error-prob")
           (or (not (:name options))
               (not (:level options)))) (exit 0 (usage summary))
      (and (= (first arguments) "del-project-errors")
           (not (:name options))) (exit 0 (usage summary)))
    ;; Execute program with options
    (println "Protean")
    (println (get-version))
    (println "\n")
    (case (first arguments)
      "projects" (projects options)
      "project" (project options)
      "project-usage" (project-usage options)
      "add-projects" (add-projects options)
      "del-project" (delete-project options)
      "add-project-error" (add-project-error options)
      "set-project-error-prob" (set-project-error-prob options)
      "del-project-errors" (del-project-errors options)
      (exit 1 (usage summary)))))
