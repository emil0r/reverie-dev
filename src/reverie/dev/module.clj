(ns reverie.dev.module
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [reverie.dev.migration.util :refer [assert-exists!
                                                create-migration
                                                format-template
                                                get-file-name
                                                get-ns-name
                                                get-project-root
                                                migration-exists?]]
            [reverie.system :as sys]
            [reverie.util :refer [join-paths get-table-name slugify]]
            [taoensso.timbre :as log]))

(defn- extend-table-name [base-name ext]
  (str/join "_" [base-name (slugify ext)]))

(defn create
  ([name] (create (get-project-root) name))
  ([{:keys [root root-path override?]
     :or {root (:root (get-project-root))
          root-path (:root-path (get-project-root))}
     :as opts} name]
   (let [module-template (slurp (io/resource "reverie/dev/templates/module/module.template"))
         module-name (str name)
         module-ns-name (get-ns-name name)
         module-file-name (get-file-name module-ns-name)
         module-path (join-paths root-path "modules" module-file-name)
         module-table-base-name (get-table-name :module name)
         migration-path (join-paths root-path "modules/migrations" module-ns-name)]
     (when (and (not override?)
                (fs/exists? (join-paths "src" module-path)))
       (throw (ex-info "Module already exists. Try using :override? true in opts" {:opts opts
                                                                                   :name name})))
     ;; clean house for migrations
     (fs/delete-dir (join-paths "src" migration-path))
     (fs/mkdirs (join-paths "src" migration-path))

     ;; copy the module-template to its new home
     ;;(fs/copy module-template (join-paths "src" module-path))
     (spit (join-paths "src" module-path)
           (format-template "reverie/dev/templates/module/module.template"
                            {:ns root
                             :module-table-base-name module-table-base-name
                             :module-ns-name module-ns-name
                             :module-name module-name
                             :migration-path migration-path}))
     (create-migration migration-path
                       "initial"
                       "reverie/dev/templates/sql/module-initial"
                       {:module-table-name (extend-table-name module-table-base-name :entity1)})
     (log/info (format "Created module %s." {:migration-path migration-path
                                             :module-path module-path})))))

(defn- get-entity-table [module-name entity-name]
  (->> (sys/module (keyword module-name)) :module :entities
       (filter #(= (:key %) entity-name))
       first
       :options
       :table))

(defn add-migration
  ([name migration-name entity-or-table-name]
   (add-migration (get-project-root) name migration-name entity-or-table-name))
  ([{:keys [root root-path]
     :or {root (:root (get-project-root))
          root-path (:root-path (get-project-root))}}
    name migration-name entity-or-table-name]
   (assert-exists! :module name)
   (let [module-ns-name (get-ns-name name)
         root-path (str/replace root #"\." "/")
         migration-path (join-paths root-path "modules/migrations" module-ns-name)
         table-name (if (string? entity-or-table-name)
                      entity-or-table-name
                      (get-entity-table name entity-or-table-name))]
     (assert (string? table-name) "entity-or-table-name needs to be a string or a known entity in the module")
     (create-migration migration-path migration-name "reverie/dev/templates/sql/migration" {:table-name table-name}))))

(defn remove-migration
  ([name migration-name] (remove-migration (get-project-root) name migration-name))
  ([{:keys [root root-path]
     :or {root (:root (get-project-root))
          root-path (:root-path (get-project-root))}}
    name migration-name]
   (let [module-ns-name (get-ns-name name)
         root-path (str/replace root #"\." "/")
         migration-path (join-paths root-path "modules/migrations" module-ns-name)
         files (fs/find-files (join-paths "src" migration-path) (re-pattern (format ".+%s\\.(up|down).sql" migration-name)))]
     (doseq [file files]
       (fs/delete file)))))
