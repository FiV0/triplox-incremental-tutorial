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

;; Let us first add some users and teams. We are going to add 4 users and 2 teams, with
;; one user being in both teams of sizes 3 and 2 respectively. Let us first subscribe to
;; and incremental query that gives us user team pairs.

(def user+team-sub
  (tc/subscribe conn '{:find [?user-name ?team-name]
                       :where [[?user :user/name ?user-name]
                               [?user :user/team ?team]
                               [?team :team/name ?team-name]]}))

(tc/transact conn
             ;; teams
             [{:db/id "team-frontend" :team/name "Frontend"}
              {:db/id "team-backend" :team/name "Backend"}

              ;; users
              {:user/handle "ada"
               :user/name   "Ada Lovelace"
               :user/team   "team-frontend"}
              {:user/handle "alan"
               :user/name   "Alan Turing"
               :user/team   "team-frontend"}
              {:db/id "grace-tmpid"
               :user/handle "grace"
               :user/name   "Grace Hopper"}
              ;; TODO: We don't support vector syntax for cardinality/many attributes yet.
              [:db/add "grace-tmpid" :user/team "team-frontend"]
              [:db/add "grace-tmpid" :user/team "team-backend"]
              {:user/handle "edsger"
               :user/name   "Edsger Dijkstra"
               :user/team   "team-backend"}])

;; Ada Lovelace and Alan Turing are working on the Frontend team and
;; Dijkstra on the Backend team. Grace Hopper works on both teams.

;; Let us first run the standard query to see what the result would be.
(tc/q (tc/db conn)
      '{:find [?user-name ?team-name]
        :where [[?user :user/name ?user-name]
                [?user :user/team ?team]
                [?team :team/name ?team-name]]})

;; => [["Grace Hopper" "Frontend"]
;;     ["Grace Hopper" "Backend"]
;;     ["Edsger Dijkstra" "Backend"]
;;     ["Alan Turing" "Frontend"]
;;     ["Ada Lovelace" "Frontend"]]

;; As expected Grace Hopper appears twice. Let us now inspect initialization delta of the incremental query.

(tc/take! user+team-sub 500)
;; => [[["Ada Lovelace" "Frontend"] 1]
;;     [["Alan Turing" "Frontend"] 1]
;;     [["Edsger Dijkstra" "Backend"] 1]
;;     [["Grace Hopper" "Backend"] 1]
;;     [["Grace Hopper" "Frontend"] 1]]

;; The result matches the standard query. Let's say we are now removing the frontend team as we
;; decided to build a database (no frontend required 😉).

(tc/transact conn [[:db/retract [:team/name "Frontend"] :team/name "Frontend"]])

(tc/take! user+team-sub 500)
;; => [[["Ada Lovelace" "Frontend"] -1]
;;     [["Alan Turing" "Frontend"] -1]
;;     [["Grace Hopper" "Frontend"] -1]]

;; All person + team pairs that were part of the frontend team were removed.

;; --------------------------------------------------------------------------
;; Views
;; --------------------------------------------------------------------------
