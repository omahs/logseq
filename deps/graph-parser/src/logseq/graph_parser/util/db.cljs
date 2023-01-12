(ns logseq.graph-parser.util.db
  "Db util fns that are useful for the frontend and nbb-logseq. This may be used
  by the graph-parser soon but if not, it should be in its own library"
  (:require [cljs-time.core :as t]
            [logseq.graph-parser.date-time-util :as date-time-util]
            [logseq.graph-parser.util.page-ref :as page-ref]
            [datascript.core :as d]
            [clojure.string :as string]))

(defn date-at-local-ms
  "Returns the milliseconds representation of the provided time, in the local timezone.
For example, if you run this function at 10pm EDT in the EDT timezone on May 31st,
it will return 1622433600000, which is equivalent to Mon May 31 2021 00 :00:00."
  ([hours mins secs millisecs]
   (date-at-local-ms (.now js/Date) hours mins secs millisecs))
  ([date hours mins secs millisecs]
   (.setHours (js/Date. date) hours mins secs millisecs)))

(defn date->int
  "Given a date object, returns its journal page integer"
  [date]
  (parse-long
   (string/replace (date-time-util/ymd date) "/" "")))

(defn int->date 
  "Given a journals page integer, returns its date object" 
  [int]
  (let [str (str int)]
    (t/date-time (parse-long (subs str 0 4))
                 (parse-long (subs str 4 6))
                 (parse-long (subs str 6 8)))))

(defn resolve-input
  "Main fn for resolving advanced query :inputs"
  [db input {:keys [current-block-uuid current-page-fn]
             :or {current-page-fn (constantly nil)}}]
  (cond
    ;; page and block inputs
    (= :current-page input)
    (some-> (current-page-fn) string/lower-case)
    (and current-block-uuid (= :current-block input))
    (:db/id (d/entity db [:block/uuid current-block-uuid]))
    (and current-block-uuid (= :parent-block input))
    (:db/id (:block/parent (d/entity db [:block/uuid current-block-uuid])))

    ;; journal date inputs
    (= :today input)
    (date->int (t/today))
    (= :yesterday input)
    (date->int (t/minus (t/today) (t/days 1)))
    (= :tomorrow input)
    (date->int (t/plus (t/today) (t/days 1)))
    (and (= :journal-day input) 
         (some-> (d/entity db [:block/uuid current-block-uuid]) :block/page :block/journal-day))
    (-> (d/entity db [:block/uuid current-block-uuid]) :block/page :block/journal-day)
    ; e.g. :3d-before-journal-day
    (and (keyword? input) 
         (re-find #"^\d+d-before-journal(-day)$" (name input))
         (some-> (d/entity db [:block/uuid current-block-uuid]) :block/page :block/journal-day))
    (let [journal-day (-> (d/entity db [:block/uuid current-block-uuid]) :block/page :block/journal-day)
          days (parse-long (re-find #"^\d+" (name input)))
          date (int->date journal-day)]
      (date->int (t/minus date (t/days days))))
    ; e.g. :3d-after-journal-day
    (and (keyword? input) 
         (re-find #"^\d+d-after-journal(-day)$" (name input))
         (some-> (d/entity db [:block/uuid current-block-uuid]) :block/page :block/journal-day))
    (let [journal-day (-> (d/entity db [:block/uuid current-block-uuid]) :block/page :block/journal-day)
          days (parse-long (re-find #"^\d+" (name input)))
          date (int->date journal-day)]
      (date->int (t/plus date (t/days days))))
    ;; e.g. :3d-before
    (and (keyword? input)
         (re-find #"^\d+d(-before)?$" (name input)))
    (let [input (name input)
          days (parse-long (re-find #"^\d+" input))]
      (date->int (t/minus (t/today) (t/days days))))
    ;; e.g. :3d-after
    (and (keyword? input)
         (re-find #"^\d+d(-after)?$" (name input)))
    (let [input (name input)
          days (parse-long (re-find #"^\d+" input))]
      (date->int (t/plus (t/today) (t/days days))))

    ;; timestamp inputs
    (= :right-now-ms input) (date-time-util/time-ms)
    (= :start-of-today-ms input) (date-at-local-ms 0 0 0 0)
    (= :end-of-today-ms input) (date-at-local-ms 24 0 0 0)
    ;; e.g. :3d-before-ms
    (and (keyword? input)
         (re-find #"^\d+d-before-ms$" (name input)))
    (let [input (name input)
          days (parse-long (re-find #"^\d+" input))]
      (date-at-local-ms (t/minus (t/today) (t/days days)) 0 0 0 0))
    ;; e.g. :3d-after-ms
    (and (keyword? input)
         (re-find #"^\d+d-after-ms$" (name input)))
    (let [input (name input)
          days (parse-long (re-find #"^\d+" input))]
      (date-at-local-ms (t/plus (t/today) (t/days days)) 24 0 0 0))

    (and (string? input) (page-ref/page-ref? input))
    (-> (page-ref/get-page-name input)
        (string/lower-case))

    :else
    input))
