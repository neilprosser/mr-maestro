(ns maestro.block-devices-test
  (:require [maestro.block-devices :refer :all]
            [midje.sweet :refer :all]))

(fact "that creating an EBS volume using nil gives sensible defaults"
      (ebs-volume nil) => {:ebs {:volume-size 10
                                 :volume-type "standard"}})

(fact "that we can override the size of an EBS volume"
      (:ebs (ebs-volume {:size 5})) => (contains {:volume-size 5}))

(fact "that we can override the type of an EBS volume"
      (:ebs (ebs-volume {:type "io1"})) => (contains {:volume-type "io1"}))

(fact "that specifying delete-on-termination works"
      (:ebs (ebs-volume {:delete-on-termination false})) => (contains {:delete-on-termination false}))

(fact "that specifying IOPS works"
      (:ebs (ebs-volume {:iops 3000})) => (contains {:iops 3000}))

(fact "that speficying a snapshot-id works"
      (:ebs (ebs-volume {:snapshot-id "snap-deadbeef"})) => (contains {:snapshot-id "snap-deadbeef"}))

(fact "that creating instance store mappings works"
      (instance-store-block-device-mappings 3) => [{:virtual-name "ephemeral0"}
                                                   {:virtual-name "ephemeral1"}
                                                   {:virtual-name "ephemeral2"}])

(fact "that creating a device name for an HVM device works"
      (device-name 3 "hvm") => "/dev/xvdd")

(fact "that creating a device name for the first Paravirtual device works"
      (device-name 0 "para") => "/dev/sda1")

(fact "that creating a device name for subsequent Paravirtual devices works"
      (device-name 3 "para") => "/dev/sdd")

(fact "that assigning device names for nil works"
      (assign-device-names nil "hvm") => [])

(fact "that assigning device names for an empty list works"
      (assign-device-names [] "para") => [])

(fact "that assigning device names for HVM works"
      (assign-device-names [{} {} {}] "hvm") => [{:device-name "/dev/xvda"}
                                                 {:device-name "/dev/xvdb"}
                                                 {:device-name "/dev/xvdc"}])

(fact "that assigning device names for Paravirtual works"
      (assign-device-names [{} {} {}] "para") => [{:device-name "/dev/sda1"}
                                                  {:device-name "/dev/sdb"}
                                                  {:device-name "/dev/sdc"}])

(fact "that creating mappings with nil instance stores count defaults to zero"
      (create-mappings nil nil nil "hvm") => [{:device-name "/dev/xvda"
                                               :ebs {:volume-size 10
                                                     :volume-type "standard"}}])

(fact "that creating mappings for HVM works"
      (create-mappings {} 3 [{} {:size 24 :type "gp2"}] "hvm")
      => [{:device-name "/dev/xvda"
           :ebs {:volume-size 10 :volume-type "standard"}}
          {:device-name "/dev/xvdb"
           :virtual-name "ephemeral0"}
          {:device-name "/dev/xvdc"
           :virtual-name "ephemeral1"}
          {:device-name "/dev/xvdd"
           :virtual-name "ephemeral2"}
          {:device-name "/dev/xvde"
           :ebs {:volume-size 10 :volume-type "standard"}}
          {:device-name "/dev/xvdf"
           :ebs {:volume-size 24 :volume-type "gp2"}}])

(fact "that creating mappings for Paravirtual works"
      (create-mappings {} 3 [{} {:size 24 :type "gp2"}] "para")
      => [{:device-name "/dev/sda1"
           :ebs {:volume-size 10 :volume-type "standard"}}
          {:device-name "/dev/sdb"
           :virtual-name "ephemeral0"}
          {:device-name "/dev/sdc"
           :virtual-name "ephemeral1"}
          {:device-name "/dev/sdd"
           :virtual-name "ephemeral2"}
          {:device-name "/dev/sde"
           :ebs {:volume-size 10 :volume-type "standard"}}
          {:device-name "/dev/sdf"
           :ebs {:volume-size 24 :volume-type "gp2"}}])
