(ns memefactory.server.contract.registry-entry
  (:require
    [bignumber.core :as bn]
    [camel-snake-kebab.core :as cs :include-macros true]
    [cljs-solidity-sha3.core :refer [solidity-sha3]]
    [cljs-web3.eth :as web3-eth]
    [district.server.smart-contracts :refer [contract-call instance contract-address]]
    [memefactory.server.contract.dank-token :as dank-token]
    [memefactory.server.contract.minime-token :as minime-token]
    [memefactory.shared.contract.registry-entry :refer [parse-status parse-load-registry-entry parse-voter vote-option->num]]))

(defn registry [contract-addr]
  (contract-call [:meme contract-addr] :registry))

(defn status
  [contract-addr]
  (parse-status (contract-call [:meme contract-addr] :status)))

(defn load-registry-entry [contract-addr]
  (parse-load-registry-entry
    contract-addr
    (contract-call (instance :meme contract-addr) :load-registry-entry contract-addr)))

(defn create-challenge [contract-addr {:keys [:challenger :meta-hash]} & [opts]]
  (contract-call (instance :meme contract-addr) :create-challenge challenger meta-hash (merge opts {:gas 1200000})))

(defn create-challenge-data [{:keys [:challenger :meta-hash]}]
  (web3-eth/contract-get-data (instance :meme) :create-challenge challenger meta-hash))

(defn approve-and-create-challenge [contract-addr {:keys [:amount] :as args} & [opts]]
  (dank-token/approve-and-call {:spender contract-addr
                                :amount amount
                                :extra-data (create-challenge-data (merge {:challenger (:from opts)} args))}
                               (merge opts {:gas 1200000})))

(defn commit-vote [contract-addr {:keys [:voter :vote-option :salt]} & [opts]]
  (contract-call (instance :meme contract-addr) :commit-vote voter (solidity-sha3 (vote-option->num vote-option) salt) (merge opts {:gas 1200000})))

(defn commit-vote-data [{:keys [:voter :vote-option :salt]}]
  (web3-eth/contract-get-data (instance :meme) :commit-vote voter (solidity-sha3 (vote-option->num vote-option) salt)))

(defn approve-and-commit-vote [contract-addr {:keys [:voting-token :amount] :as args} & [opts]]
  (minime-token/approve-and-call [:DANK voting-token]
                                 {:spender contract-addr
                                  :amount amount
                                  :extra-data (commit-vote-data (merge {:voter (:from opts)} args))}
                                 (merge opts {:gas 1200000})))

(defn reveal-vote [contract-addr {:keys [:vote-option :salt]} & [opts]]
  (contract-call (instance :meme contract-addr) :reveal-vote (vote-option->num vote-option) salt (merge opts {:gas 500000})))

(defn claim-voter-reward [contract-addr & [opts]]
  (contract-call (instance :meme contract-addr) :claim-voter-reward (:from opts) (merge opts {:gas 500000})))

(defn voter [contract-addr voter-address]
  (parse-voter voter-address (contract-call (instance :meme contract-addr) :voter voter-address)))

(defn voter-reward [contract-addr voter-address]
  (contract-call (instance :meme contract-addr) :voter-reward voter-address))

