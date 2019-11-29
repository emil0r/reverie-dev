(ns reverie.dev.app
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [reverie.dev.migration.util :refer [assert-exists!
                                                format-template
                                                get-file-name
                                                get-ns-name
                                                get-project-root]]
            [reverie.system :as sys]
            [reverie.util :refer [join-paths]]
            [taoensso.timbre :as log]))


(defn create
  ([name] (create (get-project-root) name))
  ([{:keys [root root-path override?]
     :or {root (:root (get-project-root))
          root-path (:root-path (get-project-root))}
     :as opts} name]
   (let [app-template (slurp (io/resource "reverie/dev/templates/app/app.template"))
         app-name (str name)
         app-ns-name (get-ns-name name)
         app-file-name (get-file-name app-ns-name)
         app-path (join-paths root-path "apps" app-file-name)]
     (when (and (not override?)
                (fs/exists? (join-paths "src" app-path)))
       (throw (ex-info "App already exists. Try using :override? true in opts" {:opts opts
                                                                                :name name})))
     ;; copy the app-template to its new home
     ;;(fs/copy app-template (join-paths "src" app-path))
     (spit (join-paths "src" app-path)
           (format-template "reverie/dev/templates/app/app.template"
                            {:ns root
                             :app-ns-name app-ns-name
                             :app-name app-name}))
     (log/info (format "Created app %s." {:app-path app-path})))))
