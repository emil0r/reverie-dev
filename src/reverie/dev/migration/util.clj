(ns reverie.dev.migration.util
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [migratus.core :as migratus]
            [reverie.system :as sys]
            [reverie.util :refer [join-paths]]
            [taoensso.timbre :as log]))

(defn get-project-root []
  (let [project (->> "project.clj"
                     slurp
                     edn/read-string
                     (drop 2)
                     (cons :version)
                     (apply hash-map))]
    (if-let [main (:main project)]
      (let [root (-> main str (str/replace #"(\.[\w\d]+)?$" ""))]
        {:root root :root-path (str/replace root #"\." "/" )})
      (throw (ex-info "Unable to find a main entry point for the project. This is required for finding the root path for the site." project)))))

(defn get-ns-name [name]
  (clojure.core/name name))
(defn get-file-name [name]
  (str (str/replace name #"-" "_") ".clj"))
(defn format-template [template-path data]
  (let [template (slurp (io/resource template-path))]
    (reduce (fn [out [k v]]
              (.replace out (format "{%s}" (name k)) (name v)))
            template data)))

(defn migration-exists? [migration-path migration-name]
  (not (empty? (fs/find-files (join-paths "src" migration-path) (re-pattern (format "^\\d+-%s.+\\.sql" migration-name))))))

(defn create-migration [migration-path migration-name migration-template data]
  (when (migration-exists? migration-path migration-name)
    (throw (ex-info "Migration already exists" {:migration-path migration-path
                                                :migration-name migration-name})))
  (migratus/create {:store :database
                    :migration-dir migration-path}
                   migration-name)
  
  (let [migration-template-up (str migration-template ".up.sql.template")
        migration-template-down (str migration-template ".down.sql.template")
        pattern (re-pattern (format ".+%s\\.(up|down)\\.sql" migration-name))
        files (fs/find-files (join-paths "src" migration-path) pattern)]
    (doseq [file files]
      (condp = (last (re-find #"\.(up|down)" (.getName file)))
        "up"   (spit file
                     (format-template migration-template-up data))
        "down" (spit file
                     (format-template migration-template-down data))
        :nothing))))

(defn assert-exists! [type name]
  (let [types #{:object :module :raw-page :app}
        f (case type
            :object sys/object
            :module sys/module
            :raw-page sys/raw-page
            :app sys/app
            nil)]
    (assert (types type) (format "Missing type: %s" types))
    (when-not (f (keyword name))
      (log/warn (format "Missing %s loaded" type) {:name name})
      (throw (ex-info (format "Missing %s loaded" type) {:name name})))))
