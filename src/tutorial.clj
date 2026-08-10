(ns tutorial
  (:require [xyz.triplox.api :as tc]))

(def conn (tc/connect "localhost" 5490))

;; --------------------------------------------------------------------------
;; Schema definition
;; --------------------------------------------------------------------------

;; We are going to consider a minimal issue tracker with the entity types user,
;; team, issue, status and comments.

(def schema
  ;; users
  [{:db/ident :user/handle
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity}
   {:db/ident :user/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :user/team
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}

   ;; teams
   {:db/ident :team/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity}

   ;; issues
   {:db/ident :issue/title
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :issue/status
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :issue/priority
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident :issue/assignee
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :issue/label
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many}
   {:db/ident :issue/blocks
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}

   ;; status enum
   {:db/ident :status/open}
   {:db/ident :status/in-progress}
   {:db/ident :status/closed}

   ;; comments
   {:db/ident :comment/issue
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :comment/author
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :comment/body
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}])

;; commit the schema
(tc/transact conn schema)

;; --------------------------------------------------------------------------
;; Queries
;; --------------------------------------------------------------------------

;; *joins and unification*

;; Let us first add some users and teams. We are going to 4 users and 2 teams.
