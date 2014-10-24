(ns exploud.block-devices
  (:require [exploud.util :as util]))

(defn ebs-volume
  [{:keys [delete-on-termination iops size snapshot-id type]}]
  {:ebs (util/remove-nil-values {:delete-on-termination delete-on-termination
                                 :iops iops
                                 :snapshot-id snapshot-id
                                 :volume-size (or size 10)
                                 :volume-type (or type "standard")})})

(defn instance-store-block-device-mappings
  [count]
  (map (fn [i] {:virtual-name (str "ephemeral" i)}) (range count)))

(defn device-name
  [index virtualisation-type]
  (if (= virtualisation-type "hvm")
    (str "/dev/xvd" (util/char-for-index index))
    (str "/dev/sd" (util/char-for-index index) (when (= 0 index) "1"))))

(defn assign-device-names
  [devices virtualisation-type]
  (map-indexed (fn [i device] (assoc device :device-name (device-name i virtualisation-type))) devices))

(defn create-mappings
  [root instance-stores block-devices virtualisation-type]
  (assign-device-names (concat [(ebs-volume root)] (instance-store-block-device-mappings (or instance-stores 0)) (map ebs-volume block-devices)) virtualisation-type))
