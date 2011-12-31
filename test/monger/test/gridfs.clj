(ns monger.test.gridfs
  (:refer-clojure :exclude [count remove find])
  (:use [clojure.test]
        [monger.core :only [count]]
        [monger.test.fixtures]
        [monger operators conversion]
        [monger.gridfs :only (store make-input-file)])
  (:require [monger.gridfs :as gridfs]
            [monger.test.helper :as helper]
            [clojure.java.io :as io])
  (:import [java.io InputStream File]
           [com.mongodb.gridfs GridFS GridFSInputFile]))


(defn purge-gridfs
  [f]
  (gridfs/remove-all)
  (f)
  (gridfs/remove-all))

(use-fixtures :each purge-gridfs)

(helper/connect!)



(deftest test-storing-files-to-gridfs-using-relative-fs-paths
  (let [input "./test/resources/mongo/js/mapfun1.js"]
    (is (= 0 (count (gridfs/all-files))))
    (store (make-input-file input)
      (.setFilename "monger.test.gridfs.file1")
      (.setContentType "application/octet-stream"))
    (is (= 1 (count (gridfs/all-files))))))


(deftest test-storing-files-to-gridfs-using-file-instances
  (let [input (io/as-file "./test/resources/mongo/js/mapfun1.js")]
    (is (= 0 (count (gridfs/all-files))))
    (store (make-input-file input)
      (.setFilename "monger.test.gridfs.file2")
      (.setContentType "application/octet-stream"))
    (is (= 1 (count (gridfs/all-files))))))

(deftest test-storing-bytes-to-gridfs
  (let [input (.getBytes "A string")]
    (is (= 0 (count (gridfs/all-files))))
    (store (make-input-file input)
      (.setFilename "monger.test.gridfs.file3")
      (.setContentType "application/octet-stream"))
    (is (= 1 (count (gridfs/all-files))))))

(deftest test-storing-files-to-gridfs-using-absolute-fs-paths
  (let [tmp-file (File/createTempFile "monger.test.gridfs" "test-storing-files-to-gridfs-using-absolute-fs-paths")
        _        (spit tmp-file "Some content")
        input    (.getAbsolutePath tmp-file)]
    (is (= 0 (count (gridfs/all-files))))
    (store (make-input-file input)
      (.setFilename "monger.test.gridfs.file4")
      (.setContentType "application/octet-stream"))
    (is (= 1 (count (gridfs/all-files))))))

(deftest test-finding-individual-files-on-gridfs
  (let [input   "./test/resources/mongo/js/mapfun1.js"
        ct      "binary/octet-stream"
        filename "monger.test.gridfs.file5"
        md5      "14a09deabb50925a3381315149017bbd"
        stored  (store (make-input-file input)
                  (.setFilename filename)
                  (.setContentType ct))]
    (is (= 1 (count (gridfs/all-files))))
    (is (:_id stored))
    (is (:uploadDate stored))
    (is (= 62 (:length stored)))
    (is (= md5 (:md5 stored)))
    (is (= filename (:filename stored)))
    (is (= ct (:contentType stored)))
    (are [a b] (is (= a (:md5 (gridfs/find-one-as-map b))))
         md5 (:_id stored)
         md5 filename
         md5 (to-db-object { :md5 md5 }))))
