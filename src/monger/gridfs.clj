(ns monger.gridfs
  (:refer-clojure :exclude [remove])
  (:require [monger.core]
            [clojure.java.io :as io])
  (:use [monger.conversion])
  (:import [com.mongodb DBObject]
           [com.mongodb.gridfs GridFS GridFSInputFile]
           [java.io InputStream File]))

;;
;; Implementation
;;

(def
  ^{:doc "Type object for a Java primitive byte array."
    :private true
    }
  byte-array-type (class (make-array Byte/TYPE 0)))

(def ^:dynamic *chunk-size* (* 2 1024 1024))

;; ...



;;
;; API
;;


(defn remove
  ([]
     (remove {}))
  ([query]
     (.remove ^GridFS monger.core/*mongodb-gridfs* ^DBObject (to-db-object query))))

(defn remove-all
  []
  (remove {}))

(defn all-files
  ([]
     (.getFileList ^GridFS monger.core/*mongodb-gridfs*))
  ([query]
     (.getFileList ^GridFS monger.core/*mongodb-gridfs* query)))


(defprotocol GridFSInputFileFactory
  (^GridFSInputFile make-input-file [input] "Makes GridFSInputFile out of given input"))

(extend byte-array-type
  GridFSInputFileFactory
  { :make-input-file (fn [^bytes input]
                       (.createFile ^GridFS monger.core/*mongodb-gridfs* input)) })

(extend-protocol GridFSInputFileFactory
  String
  (make-input-file [^String input]
    (.createFile ^GridFS monger.core/*mongodb-gridfs* ^InputStream (io/make-input-stream input { :encoding "UTF-8" })))

  File
  (make-input-file [^File input]
    (.createFile ^GridFS monger.core/*mongodb-gridfs* ^InputStream (io/make-input-stream input { :encoding "UTF-8" }))))


(defmacro store
  [^GridFSInputFile input & body]
  `(let [^GridFSInputFile f# (doto ~input ~@body)]
     (.save f# *chunk-size*)
     (from-db-object f# true)))


(defprotocol Finders
  (find-one        [input] "Finds one file using given input (an ObjectId, filename or query)")
  (find-one-as-map [input] "Finds one file using given input (an ObjectId, filename or query), converting result to Clojure map before returning"))

(extend-protocol Finders
  String
  (find-one [^String input]
    (.findOne ^GridFS monger.core/*mongodb-gridfs* input))
  (find-one-as-map [^String input]
    (from-db-object (find-one input) true))

  org.bson.types.ObjectId
  (find-one [^org.bson.types.ObjectId input]
    (.findOne ^GridFS monger.core/*mongodb-gridfs* input))
  (find-one-as-map [^org.bson.types.ObjectId input]
    (from-db-object (find-one input) true))


  DBObject
  (find-one [^DBObject input]
    (.findOne ^GridFS monger.core/*mongodb-gridfs* input))
  (find-one-as-map [^DBObject input]
    (from-db-object (find-one input) true)))


