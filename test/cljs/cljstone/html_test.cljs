(ns cljstone.html-test
  (:require [cljs.test :refer-macros [async deftest is use-fixtures]]
            [schema.core :as s :refer-macros [with-fn-validation]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:use [cljs.core.async :only [chan <! >! put!]]
        [clojure.string :only [trim]]
        [cljstone.html :only [draw-card draw-minion-card draw-minion get-character-id-from-event]]
        [cljstone.test-helpers :only [boulderfist-card boulderfist-minion fresh-board]]))

(def test-board (-> fresh-board
                    (assoc :whose-turn :player-2)
                    (assoc-in [:player-2 :hand 0] boulderfist-card)))

(deftest drawing-cards
  (with-fn-validation
    (let [game-event-chan (chan)
          card (draw-card boulderfist-card 0 :player-2 true game-event-chan)
          props (nth card 1)]
      (is (= (trim (props :class)) "card neutral minion playable"))
      (is (= (props :data-card-index) 0))
      (is (= (nth card 2) [draw-minion-card boulderfist-card]))

      ; fire a click event, test to see if something was put in game-event-chan
      ((props :on-click) 'foo)

      (async done
         (go
           (is (= (<! game-event-chan)
                  {:type :play-card
                   :player :player-2
                   :index 0}))
           (done))))))

(deftest drawing-minions
  (with-fn-validation
    (let [mouse-event-chan (chan)
          minion (draw-minion boulderfist-minion test-board true mouse-event-chan)
          props (nth minion 1)]
      (is (= (trim (props :class)) "minion can-attack"))
      (is (= (props :data-character-id) 12345))
      (is (= (props :draggable) true))

      (let [fake-mouse-event (clj->js {"type" "foo"
                                       "preventDefault" (fn [])})]
        ; fire all of the mouse events we support, verify that correct-looking events are emitted
        (with-redefs [get-character-id-from-event (fn [e] 12345)]
          ((props :on-click) fake-mouse-event)
          ((props :on-drag-start) fake-mouse-event)
          ; on-drag-over just calls .preventDefault, doesn't put anything in mouse-event-chan
          ((props :on-drag-over) fake-mouse-event)
          ((props :on-drop) fake-mouse-event)

          (async done
            (go
              (doseq [i (range 3)]
                (is (= (<! mouse-event-chan)
                       {:type :mouse-event
                        :mouse-event-type :foo
                        :board test-board
                        :character-id 12345})))
              (done))))))))

(deftest drawing-end-turn-button)

(deftest drawing-board-mode)

(deftest handling-mouse-events)
