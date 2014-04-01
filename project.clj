(defproject protean-cli "0.2.2"
  :description "Command line interface for Protean"
  :url "http://github.com/passivsystems/protean-cli"
  :license {:name "Apache License v2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.cli "0.3.1"]
                 [clj-http "0.9.0"]
                 [cheshire "5.3.1"]
                 [com.taoensso/timbre "3.1.1"]]
  :aot :all
  :main protean.cli)
