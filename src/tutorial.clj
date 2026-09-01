(ns tutorial
  (:require [xyz.triplox.api :as tc]
            [clojure.core.async :as async])
  (:import (java.io Closeable)))

(def conn (tc/connect "localhost" 5490))

;; --------------------------------------------------------------------------
;; Schema definition
;; --------------------------------------------------------------------------

;; We are going to consider a minimal issue tracker with the entity types `user`,
;; `team`, `issue` and `status`.

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

   ;; status enum
   {:db/ident :status/open}
   {:db/ident :status/in-progress}
   {:db/ident :status/closed}])

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

(def team-tx-key
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
                 :user/team   "team-backend"}]))

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

;; *NOT*

;; To illustrate the `not` clause, let us observe tickets that are not clsoed yet.

(def !not-closed-sub
  (tc/subscribe conn '{:find [?title]
                       :where [[?i :issue/title ?title]
                               [?i :issue/status ?status]
                               (not [?status :db/ident :status/closed])]}))

(tc/take! !not-closed-sub)
;; => [[["Add dark mode to the dashboard"] 1]
;;     [["Flaky test in the bid pipeline"] 1]
;;     [["Incremental joins allocate per delta"] 1]
;;     [["Sync engine drops updates on reconnect"] 1]]

(tc/transact conn [[:db/add [:issue/key "TPX-6"] :issue/status :status/closed]])

(tc/take! !not-closed-sub)
;; => [[["Flaky test in the bid pipeline"] -1]]

;; When closing the flaky ticket, it leaves the "not-closed" set.

;; TODO: replace with untriaged below
#_
(def untriaged-sub
  (tc/subscribe conn '{:find [?title]
                       :where [[?i :issue/title ?title]
                               (not [?i :issue/label ?l])]}))

;; *AGGREGATES*

;; TODO Write the tutorial in such a way that we still have the Frontend and Backend team at this point.

(def frontend-team-id
  (ffirst (tc/q (tc/db conn team-tx-key)
                '{:find [?team-eid]
                  :where [[?team-eid :team/name "Frontend"]]})))

;; Just so we have the Frontend team back (You know Database need analytics dashboards ...).

(tc/transact conn [[:db/add frontend-team-id :team/name "Frontend"]])

;; With an aggregate in the find clause there will always be a tuple that gets removed and a tuple
;; that gets added when an aggregate changes. To illustrate incremental aggregate queries
;; we are going to look at the number of tickets per team and status.

(def !issue-count-by-team+status
  (tc/subscribe conn '{:find [?team-name ?status-name (count ?issue)]
                       :where [[?issue :issue/assignee ?user]
                               [?user :user/team ?team]
                               [?team :team/name ?team-name]
                               [?issue :issue/status ?status]
                               [?status :db/ident ?status-name]]}))


(tc/take! !issue-count-by-team+status)
;; => [[["Backend" :status/closed 2] 1]
;;     [["Backend" :status/open 1] 1]
;;     [["Frontend" :status/closed 1] 1]
;;     [["Frontend" :status/in-progress 2] 1]
;;     [["Frontend" :status/open 2] 1]]

;; Let's say ticket 6 gets closed.

(tc/transact conn [[:db/add [:issue/key "TPX-6"] :issue/status :status/closed]])

(tc/take! !issue-count-by-team+status)
;; => [[["Frontend" :status/closed 2] 1]
;;     [["Frontend" :status/closed 1] -1]
;;     [["Frontend" :status/in-progress 2] -1]
;;     [["Frontend" :status/in-progress 1] 1]]

;; We can see that the ticket was assigned to the Frontend team and that the closed count got bumped from 1 to 2 where as
;; the in-progress count got bumped from 2 to 1.

;; *OR-JOIN + NOT-JOIN + RULES*
;; TODO
;; We currently don't support or-join, not-join and rules but they will (eventually) come to Triplox.

;; --------------------------------------------------------------------------
;; Views
;; --------------------------------------------------------------------------

;; We currently don't implement views server side. If there is a big demand for it
;; we might add them some day server side. Here is an example of how you might implement
;; them client side.

(defn update-view [view-map inc-res]
  (reduce (fn [view-map [tuple value]]
            (let [new-val  (+ (get view-map tuple 0) value)]
              (if-not (zero? new-val)
                (assoc view-map tuple new-val)
                (dissoc view-map tuple))))
          view-map
          inc-res))

(defn update-view! [!view inc-res]
  (swap! !view update-view inc-res))

;; A worker that checks the subscription every 500ms and updates the view.
(defn start-worker [sub !view]
  (let [stop (async/chan)
        done (async/chan)]
    (async/go-loop []
      (let [[_ ch] (async/alts! [stop (async/timeout 300)])]
        (if (= ch stop)
          (async/close! done)
          (do (loop [delta (tc/take! sub 10)]
                (when (not= delta ::tc/timeout)
                  (update-view! !view delta)
                  (recur (tc/take! sub 10))))
              (recur)))))
    {:stop stop :done done}))

(defrecord View [sub view stop-chan done-chan]
  Closeable
  (close [_this]
    (async/close! stop-chan)
    (async/<!! done-chan)
    (.close sub)))

(defn ->view [conn q]
  (let [sub (tc/subscribe conn q)
        !view (atom {})
        {:keys [stop done]} (start-worker sub !view)]
    (->View sub !view stop done)))

(defn get-view [{:keys [view]}]
  (vec (keys @view)))

;; creating a new view to get a new db
(def conn (tc/connect "localhost" 5490))

(tc/transact conn [{:db/ident :person/name
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/unique :db.unique/identity}
                   {:db/ident :person/residence
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one}])

(tc/transact conn [{:person/name "Ada Lovelace"
                    :person/residence "12 St. James's Square"}
                   {:person/name "Alan Turing"
                    :person/residence "Bletchley Park"}])

(def residence-view (->view conn '{:find [?name ?residence]
                                   :where [[?p :person/name ?name]
                                           [?p :person/residence ?residence]]}))

(get-view residence-view)
;; => [["Ada Lovelace" "12 St. James's Square"]
;;     ["Alan Turing" "Bletchley Park"]]

(tc/transact conn [[:db/add [:person/name "Ada Lovelace"] :person/residence "Buckingham Palace"]])

(get-view residence-view)
;; => [["Alan Turing" "Bletchley Park"]
;;     ["Ada Lovelace" "Buckingham Palace"]]
