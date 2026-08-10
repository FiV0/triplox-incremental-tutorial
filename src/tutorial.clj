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
   {:db/ident :issue/key
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity}
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

;; *JOINS and UNIFICATION*

;; Let us first add some users and teams. We are going to add 4 users and 2 teams, with
;; one user being in both teams of sizes 3 and 2 respectively. Let us first subscribe to
;; and incremental query that gives us user team pairs.

(def !user+team-sub
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

(tc/take! !user+team-sub 500)
;; => [[["Ada Lovelace" "Frontend"] 1]
;;     [["Alan Turing" "Frontend"] 1]
;;     [["Edsger Dijkstra" "Backend"] 1]
;;     [["Grace Hopper" "Backend"] 1]
;;     [["Grace Hopper" "Frontend"] 1]]

;; The result matches the standard query. Let's say we are now removing the frontend team as we
;; decided to build a database (no frontend required 😉).

(tc/transact conn [[:db/retract [:team/name "Frontend"] :team/name "Frontend"]])

(tc/take! !user+team-sub 500)
;; => [[["Ada Lovelace" "Frontend"] -1]
;;     [["Alan Turing" "Frontend"] -1]
;;     [["Grace Hopper" "Frontend"] -1]]

;; All person + team pairs that were part of the frontend team were removed.

;; *PREDICATES and FUNCTIONS*

;; Let us now create a query that uses predicates and functions. It gets the issues
;; with a high priority, their assignee and the corresponding SLA response time in hours.

(def !urgent-issues-sub
  (tc/subscribe
   conn
   '{:find [?title ?assignee-name ?sla-hours]
     :where [[?issue :issue/title ?title]
             [?issue :issue/priority ?priority]
             [(<= ?priority 2)]
             [?issue :issue/assignee ?assignee]
             [?assignee :user/name ?assignee-name]
             [(* ?priority 24) ?sla-hours]]}))

(tc/transact conn
             [{:issue/key "TPX-1"
               :issue/title "Sync engine drops updates on reconnect"
               :issue/priority 1
               :issue/assignee [:user/handle "ada"]}
              {:issue/key "TPX-2"
               :issue/title "Add dark mode to the dashboard"
               :issue/priority 4
               :issue/assignee [:user/handle "alan"]}
              {:issue/key "TPX-3"
               :issue/title "Incremental joins allocate per delta"
               :issue/priority 2
               :issue/assignee [:user/handle "grace"]}
              {:issue/key "TPX-4"
               :issue/title "Document the tutorial schema"
               :issue/priority 5
               :issue/assignee [:user/handle "edsger"]}
              {:issue/key "TPX-5"
               :issue/title "WAL replay is quadratic"
               :issue/priority 1
               :issue/assignee [:user/handle "grace"]}
              {:issue/key "TPX-6"
               :issue/title "Flaky test in the bid pipeline"
               :issue/priority 3
               :issue/assignee [:user/handle "alan"]}])

(tc/take! !urgent-issues-sub)
;; => [[["Incremental joins allocate per delta" "Grace Hopper" 48] 1]
;;     [["Sync engine drops updates on reconnect" "Ada Lovelace" 24] 1]
;;     [["WAL replay is quadratic" "Grace Hopper" 24] 1]]

;; The inc query initialization gives us the same results at the standard query. Let's say we
;; now prioritize the flake test and deprioritize the incremental join allocations.

(tc/transact conn [[:db/add [:issue/key "issue-6"] :issue/priority 2]
                   [:db/add [:issue/key "issue-3"] :issue/priority 4]])

(tc/take! !urgent-issues-sub)
;; => [[["Flaky test in the bid pipeline" "Alan Turing" 48] 1]
;;     [["Incremental joins allocate per delta" "Grace Hopper" 48] -1]]

;; The flaky tests enters the and "incremental join allocations" exists the urgent tickets.

;; *OR*

;; Let us add the status for the issues and subscribe to the active issues.

(tc/transact conn
             [[:db/add [:issue/key "TPX-1"] :issue/status :status/open]
              [:db/add [:issue/key "TPX-2"] :issue/status :status/open]
              [:db/add [:issue/key "TPX-3"] :issue/status :status/open]
              [:db/add [:issue/key "TPX-4"] :issue/status :status/closed]
              [:db/add [:issue/key "TPX-5"] :issue/status :status/in-progress]
              [:db/add [:issue/key "TPX-6"] :issue/status :status/in-progress]])

(def !active-sub
  (tc/subscribe conn '{:find [?title]
                       :where [[?i :issue/title ?title]
                               ;; TODO replace once https://github.com/FiV0/triplox/issues/313 goes in
                               #_
                               (or [?i :issue/status :status/open]
                                   [?i :issue/status :status/in-progress])
                               [?i :issue/status ?status]
                               (or [?status :db/ident :status/open]
                                   [?status :db/ident :status/in-progress])]}))

(tc/take! !active-sub)
;; => [[["Add dark mode to the dashboard"] 1]
;;     [["Flaky test in the bid pipeline"] 1]
;;     [["Incremental joins allocate per delta"] 1]
;;     [["Sync engine drops updates on reconnect"] 1]
;;     [["WAL replay is quadratic"] 1]]

;; First we all active queries. Let us now change an open to in-progress and a in-progress to closed.

(tc/transact conn [[:db/add [:issue/key "TPX-1"] :issue/status :status/in-progress]
                   [:db/add [:issue/key "TPX-5"] :issue/status :status/closed]])

(tc/take! !active-sub)
;; => [[["WAL replay is quadratic"] -1]]

;; "WAL replay" was closed hence exists the active set where as "Sync engine drops updates on reconnect" changes status, but
;; doesn't leave or enter the active set.

;; *AGGREGATES + OR-JOIN + NOT-JOIN*

;; TODO
;; We currently don't support aggregates, or-join and not-join, but they will come.

;; --------------------------------------------------------------------------
;; Views
;; --------------------------------------------------------------------------
