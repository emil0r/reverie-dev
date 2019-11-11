(ns reverie.dev.object
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
            [reverie.util :refer [join-paths get-table-name]]
            [taoensso.timbre :as log]))

(defn create
  ([name] (create (get-project-root) name))
  ([{:keys [root root-path override?]
     :or {root (:root (get-project-root))
          root-path (:root-path (get-project-root))}
     :as opts} name]
   (let [object-template (slurp (io/resource "reverie/dev/templates/object/object.template"))
         object-name (str name)
         object-ns-name (get-ns-name name)
         object-file-name (get-file-name object-ns-name)
         object-path (join-paths root-path "objects" object-file-name)
         object-table-name (get-table-name :object name)
         migration-path (join-paths root-path "objects/migrations" object-ns-name)]
     (when (and (not override?)
                (fs/exists? (join-paths "src" object-path)))
       (throw (ex-info "Object already exists. Try using :override? true in opts" {:opts opts
                                                                                   :name name})))
     ;; clean house for migrations
     (fs/delete-dir (join-paths "src" migration-path))
     (fs/mkdirs (join-paths "src" migration-path))

     ;; copy the object-template to its new home
     ;;(fs/copy object-template (join-paths "src" object-path))
     (spit (join-paths "src" object-path)
           (format-template "reverie/dev/templates/object/object.template"
                            {:ns root
                             :object-table-name object-table-name
                             :object-ns-name object-ns-name
                             :object-name object-name
                             :migration-path migration-path}))
     (create-migration migration-path "initial" "reverie/dev/templates/sql/object-initial" {:object-table-name object-table-name})
     (log/info (format "Created object %s." {:migration-path migration-path
                                             :object-path object-path})))))

(defn add-migration
  ([name migration-name] (add-migration (get-project-root) name migration-name))
  ([{:keys [root root-path]
     :or {root (:root (get-project-root))
          root-path (:root-path (get-project-root))}}
    name migration-name]
   (assert-exists! :object name)
   (let [object-ns-name (get-ns-name name)
         root-path (str/replace root #"\." "/")
         migration-path (join-paths root-path "objects/migrations" object-ns-name)
         table-name (:table (sys/object (keyword name)))]
     (create-migration migration-path migration-name "reverie/dev/templates/sql/migration" {:table-name table-name}))))

(defn remove-migration
  ([name migration-name] (remove-migration (get-project-root) name migration-name))
  ([{:keys [root root-path]
     :or {root (:root (get-project-root))
          root-path (:root-path (get-project-root))}}
    name migration-name]
   (let [object-ns-name (get-ns-name name)
         root-path (str/replace root #"\." "/")
         migration-path (join-paths root-path "objects/migrations" object-ns-name)
         files (fs/find-files (join-paths "src" migration-path) (re-pattern (format ".+%s\\.(up|down).sql" migration-name)))]
     (doseq [file files]
       (fs/delete file)))))
