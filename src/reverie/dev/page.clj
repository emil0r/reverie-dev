(ns reverie.dev.page
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
  ([name url-path] (create (get-project-root) name))
  ([{:keys [root root-path override?]
     :or {root (:root (get-project-root))
          root-path (:root-path (get-project-root))}
     :as opts} name url-path]
   (let [page-template (slurp (io/resource "reverie/dev/templates/page/page.template"))
         page-ns-name (get-ns-name name)
         page-file-name (get-file-name page-ns-name)
         page-path (join-paths root-path "endpoints" page-file-name)]
     (when (and (not override?)
                (fs/exists? (join-paths "src" page-path)))
       (throw (ex-info "Page already exists. Try using :override? true in opts" {:opts opts
                                                                                :name name})))
     ;; copy the page-template to its new home
     ;;(fs/copy page-template (join-paths "src" page-path))
     (spit (join-paths "src" page-path)
           (format-template "reverie/dev/templates/page/page.template"
                            {:ns root
                             :url-path url-path
                             :page-ns-name page-ns-name}))
     (log/info (format "Created page %s." {:page-path page-path})))))
