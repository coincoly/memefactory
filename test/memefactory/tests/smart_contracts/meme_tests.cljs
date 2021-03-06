(ns memefactory.tests.smart-contracts.meme-tests
  (:require [bignumber.core :as bn]
            [cljs-web3-next.eth :as web3-eth]
            [cljs-web3-next.evm :as web3-evm]
            [cljs.test :refer-macros [deftest is testing async]]
            [district.server.web3 :refer [web3]]
            [memefactory.server.contract.dank-token :as dank-token]
            [memefactory.server.contract.eternal-db :as eternal-db]
            [memefactory.server.contract.meme :as meme]
            [memefactory.server.contract.meme-factory :as meme-factory]
            [memefactory.server.contract.registry :as registry]
            [cljs.core.async :refer [go <!]]
            [district.shared.async-helpers :refer [<?]]
            [memefactory.tests.smart-contracts.utils :refer [tx-reverted?]]
            [memefactory.server.generator :refer [create-meme!]]))

(def sample-meta-hash-1 "QmZJWGiKnqhmuuUNfcryiumVHCKGvVNZWdy7xtd3XCkQJH")
(def sample-meta-hash-2 "JmZJWGiKnqhmuuUNfcryiumVHCKGvVNZWdy7xtd3XCkQJ9")

;;;;;;;;;;;
;; Meme  ;;
;;;;;;;;;;;

(defn create-meme
  "Creates a meme and returns its registry entry"
  [& [creator-addr deposit total-supply meta-hash :as args]]
  (go
    (try
      (:registry-entry (<! (create-meme! {:total-supply total-supply
                                          :from-account creator-addr
                                          :meta-hash meta-hash
                                          :deposit (str deposit)
                                          :max-total-supply total-supply})))
      (catch :default e
        false))))

(deftest approve-and-create-meme-test
  (async done
         (go
           (let [[creator-addr] (<! (web3-eth/accounts @web3))
                 [max-total-supply deposit] (<! (eternal-db/get-uint-values :meme-registry-db [:max-total-supply :deposit]))
                 registry-entry (<! (create-meme creator-addr deposit max-total-supply sample-meta-hash-1))]

             (testing "Meme can be created under valid conditions"
               (is registry-entry))

             (testing "Cannot create meme with higher supply than maxTotalSupply TCR param"
               (is (not (<! (create-meme creator-addr deposit (inc max-total-supply) sample-meta-hash-1)))))

             (testing "Created Meme has properties initialised as they should be"
               (let [meme (<? (meme/load-meme registry-entry))]
                 (is (= (:meme/meta-hash meme) sample-meta-hash-1))
                 (is (= (:meme/total-supply meme) (bn/number max-total-supply))))))
           (done))))

(deftest transfer-deposit-test
  (async done
         (go
           (let [[creator-addr] (<! (web3-eth/accounts @web3))
                 [max-total-supply deposit challenge-period-duration]
                 (->> (<? (eternal-db/get-uint-values :meme-registry-db [:max-total-supply :deposit :challenge-period-duration]))
                      (map bn/number))
                 registry-entry (<! (create-meme creator-addr deposit max-total-supply sample-meta-hash-1))]

             (testing "Meme deposit can't be transferred if not whitelisted"
               ;; it will not be whitelisted because we are still in challenge period
               (is (tx-reverted? (<! (meme/transfer-deposit registry-entry)))))

             (<! (web3-evm/increase-time @web3 (inc (bn/number challenge-period-duration))))

             ;; all conditions shold be valid after challenge period
             (testing "Meme deposit can be transferred from whitelisted meme to depositCollector address"
               (is (<? (meme/transfer-deposit registry-entry)))

               ;; TODO: Check collector increased balance, need to know collector address
               #_(let [collector-final-balance (bn/number (<? (dank-token/balance-of collector-address)))]
                   (is (> collector-final-balance collector-initial-balance))))

             (done)))))

(deftest mint-test
  (async done
         (go
           (let [[creator-addr] (<! (web3-eth/accounts @web3))
                 [max-total-supply deposit challenge-period-duration]
                 (->> (<? (eternal-db/get-uint-values :meme-registry-db [:max-total-supply :deposit :challenge-period-duration]))
                      (map bn/number))
                 registry-entry (<! (create-meme creator-addr deposit max-total-supply sample-meta-hash-1))]

             ;; it will not be whitelisted because we are still in challenge period
             (is (tx-reverted? (<? (meme/mint registry-entry max-total-supply {}))))

             (<! (web3-evm/increase-time @web3 (inc challenge-period-duration)))

             ;; Now meme should be whitelisted
             (let [mint-count (quot max-total-supply 2)]

               (<? (meme/mint registry-entry mint-count {}))

               (let [meme (<? (meme/load-meme registry-entry))]
                 (testing "All properties should be good after minting collectibles"
                   (is (= (:meme/total-minted meme) mint-count))
                   ;; TODO: how to do this with core.async?
                   #_(is (apply (partial = creator-addr)
                                (map (fn [token-id]
                                       (<? (meme-token/owner-of token-id)))
                                     (range (:meme/token-id-start meme)
                                            (+ (:meme/token-id-start meme)
                                               (:meme/total-minted meme)))))))

                 (testing "Mint should work with passed amount that's bigger than totalSupply"
                   (<? (meme/mint registry-entry (+ 3 max-total-supply) {}))
                   (let [meme (<? (meme/load-meme registry-entry))]
                     (is (= (:meme/total-minted meme) (:meme/total-supply meme) max-total-supply))))))
             (done)))))
