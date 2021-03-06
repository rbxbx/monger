;; Copyright (c) 2011-2012 Michael S. Klishin
;;
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Monger TestKit is an experiment that turned out to be partially successful but partially need to be
            rethough, redesigned, integrated with MongoDB DB references and simply reimplemented from the ground up
            one more time. For this exact reason, there is no documentation guide on it.
            Please keep this in mind if you are considering using it."}
  monger.testkit
  (:require [monger.collection :as mc]
            [monger.result     :as mr])
  (:use     [monger.internal.fn :only [expand-all expand-all-with]])
  (:import org.bson.types.ObjectId))


;;
;; API
;;

(defmacro defcleaner
  "Defines a fixture function that removes all documents from a collection. If collection is not specified,
   a conventionally named var will be used. Supposed to be used with clojure.test/use-fixtures but may
   be useful on its own.

   Examples:

   (defcleaner events)              ;; collection name will be taken from the events-collection var
   (defcleaner people \"accounts\") ;; collection name is given
  "
  [entities & coll-name]
  (let [coll-arg (if coll-name
                   (str (first coll-name))
                   (symbol (str entities "-collection")))
        fn-name  (symbol (str "purge-" entities))]
    `(defn ~fn-name
       [f#]
       (mc/remove ~coll-arg)
       (f#)
       (mc/remove ~coll-arg))))



(def factories (atom {}))
(def defaults  (atom {}))
(def last-oids (atom {}))

(defn defaults-for
  [f-group & { :as attributes }]
  (swap! defaults (fn [v]
                    (assoc v (name f-group) attributes))))

(defn factory
  [f-group f-name & { :as attributes }]
  (swap! factories (fn [a]
                     (assoc-in a [(name f-group) (name f-name)] attributes))))


(declare build seed remember-oid)
(defn- expand-associate-for-building
  [f]
  (let [mt               (meta f)
        [f-group f-name] (f)]
    (:_id (build f-group f-name))))

(defn- expand-for-building
  "Expands functions, treating those with association metadata (see `parent-id` for example) specially"
  [f]
  (let [mt (meta f)]
    (if (:associate-gen mt)
      (expand-associate-for-building f)
      (f))))

(defn- expand-associate-for-seeding
  [f]
  (let [mt               (meta f)
        [f-group f-name] (f)]
    (:_id (seed f-group f-name))))

(defn- expand-for-seeding
  "Expands functions, treating those with association metadata (see `parent-id` for example) specially,
   making sure parent documents are persisted first"
  [f]
  (let [mt (meta f)]
    (if (:associate-gen mt)
      (expand-associate-for-seeding f)
      (f))))

(defn build
  "Generates a new document and returns it.
   Unless _id field is defined by the factory, it is generated."
  [f-group f-name & { :as overrides }]
  (let [d          (@defaults (name f-group))
        attributes (get-in @factories [(name f-group) (name f-name)])
        merged     (merge { :_id (ObjectId.) } d attributes overrides)]
    (expand-all-with merged expand-for-building)))

(defn seed
  "Generates and inserts a new document, then returns it.
   Unless _id field is defined by the factory, it is generated."
  [f-group f-name & { :as overrides }]
  (io!
    (let [d          (@defaults (name f-group))
          attributes (get-in @factories [(name f-group) (name f-name)])
          merged     (merge { :_id (ObjectId.) } d attributes overrides)
          expanded   (expand-all-with merged expand-for-seeding)]
      (assert (mr/ok? (mc/insert f-group expanded)))
      (remember-oid f-group f-name (:_id expanded))
      expanded)))

(defn seed-all
  "Seeds all fixtures in the given collection"
  [f-group]
  (io!
    (let [xs (vec (keys (get @factories f-group)))]
      (doseq [f-name xs]
        (seed f-group f-name)))))

(defn embedded-doc
  [f-group f-name & { :as overrides }]
  (fn []
    (apply build f-group f-name (flatten (vec overrides)))))

(defn parent-id
  [f-group f-name]
  (with-meta (fn []
               [f-group f-name]) { :associate-gen true :parent-gen true }))

(defn- remember-oid
  [f-group f-name oid]
  (swap! last-oids (fn [a]
                     (assoc-in a [(name f-group) (name f-name)] oid))))

(defn last-oid-of
  "Returns last object id of a document inserted using given factory"
  [f-group f-name]
  (get-in @last-oids [(name f-group) (name f-name)]))


(def ^{ :doc "Returns a new object id. Generates it if needed, otherwise returns a cached version.
  Useful for defining referenced associations between fixture documents." }
  memoized-oid (memoize (fn [f-group f-name]
                          (ObjectId.))))
