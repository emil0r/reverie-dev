(ns reverie.dev.migration
  (:require [clojure.java.io :as io]
            [reverie.migrator :as migrator]
            [reverie.migrator.sql :refer [get-migration-table]]
            [reverie.server :as server]
            [reverie.system :as sys]))

(defmulti get-migration :type)
(defmethod get-migration :object [data]
  (let [object (sys/object (:name data))
        migration (get-in object [:options :migration])]
    (if (:path migration)
      {:migration-dir (:path migration)
       :migration-table-name (get-migration-table (merge migration data))})))
(defmethod get-migration :raw-page [data]
  (let [raw-page (sys/raw-page (:name data))
        migration (get-in raw-page [:options :migration])]
    (if (:path migration)
      {:migration-dir (:path migration)
       :migration-table-name (get-migration-table (merge migration data))})))
(defmethod get-migration :module [data]
  (let [module (sys/module (:name data))
        migration (get-in module [:options :migration])]
    (if (:path migration)
      {:migration-dir (:path migration)
       :migration-table-name (get-migration-table (merge migration data))})))
(defmethod get-migration :app [data]
  (let [app (sys/app (:name data))
        migration (get-in app [:options :migration])]
    (if (:path migration)
      {:migration-dir (:path migration)
       :migration-table-name (get-migration-table (merge migration data))})))
(defmethod get-migration :default [{:keys [type] :as data}]
  (throw (ex-info (format "type %s not supported" type) data)))

(defn get-migration-map [datasource data]
  (let [migration (get-migration data)]
    (merge migration
           data
           {:store :database
            :db datasource
            :migration-dir-valid? (if-let [migration-dir (:migration-dir migration)]
                                    (some? (io/resource migration-dir)))})))


(defn migrate [type name]
  (let [mmap (get-migration-map
              (get-in @server/system [:database :db-specs :default :datasource])
              {:type type
               :name (keyword name)})
        migrator (get-in @server/system [:migrator])]
    (migrator/migrate migrator mmap)
    nil))

(defn rollback [type name]
  (let [mmap (get-migration-map
              (get-in @server/system [:database :db-specs :default :datasource])
              {:type type
               :name (keyword name)})
        migrator (get-in @server/system [:migrator])]
    (migrator/rollback migrator mmap)
    nil))

