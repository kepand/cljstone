(ns cljstone.html
  (:require [goog.dom :as dom]
            [reagent.core :as r]
            [schema.core :as s])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:use [cljs.core.async :only [chan <! >! put!]]
        [cljs.pprint :only [pprint]]
        [cljstone.minion :only [get-attack get-health can-attack]]
        [cljstone.board :only [end-turn play-card path-to-character]]
        [cljstone.combat :only [attack]]))

(s/defschema InputEvent {:foo s/Any})

(defn- get-character-id-from-event [event]
  (-> event
      .-currentTarget
      .-dataset
      .-characterId
      js/parseInt))

(defn draw-minion-card [card]
  [:div.content
   [:div.name (:name card)]
   [:div.attack (:attack card)]
   [:div.health (:health card)]])

(defn draw-spell-card [card]
  [:div.content
   [:div.name (:name card)]])

(defn draw-card [card index player board-atom is-owners-turn game-event-chan]
  (let [playable is-owners-turn ; will become more complex later
        classes (str
                  "card "
                  (clj->js (:class card))
                  (condp = (:type card) :minion " minion " :spell " spell ")
                  (when playable "playable"))]
    [:div {:class classes
           :data-card-index index
           :on-click (fn [e]
                       (when playable
                         (put! game-event-chan {:type :play-card
                                           :player player
                                           :index index})))}
     (condp = (:type card)
       :minion [draw-minion-card card]
       :spell [draw-spell-card card])]))

(defn draw-hero [hero]
  [:div.hero
   [:div.name (:name hero)]])

(defn draw-minion [minion board board-atom is-owners-turn mouse-event-chan]
  (let [minion-can-attack (and is-owners-turn
                               (can-attack minion)
                               (= (board :mode) :default))
        classes (str
                  "minion "
                  (when minion-can-attack "can-attack"))
        put-event-in-chan (fn [e]
                            (put! mouse-event-chan {:type :mouse-event
                                                    :mouse-event-type (.-type e)
                                                    :board board
                                                    :character-id (get-character-id-from-event e)}))]
    [:div {:class classes
           :data-character-id (:id minion)
           :draggable minion-can-attack
           :on-drag-start put-event-in-chan
           :on-drag-over #(.preventDefault %)
           :on-drop (fn [e]
                      (put-event-in-chan e)
                      (.preventDefault e))}
     [:div.name (:name minion)]
     [:div.attack (get-attack minion)]
     [:div.health (get-health minion)]]))

(defn draw-board-half [board board-atom player whose-turn game-event-chan mouse-event-chan]
  (let [board-half (board player)
        is-owners-turn (= whose-turn player)]
    [:div.board-half
     [:div.hand
      [:h3 (:name (:hero board-half))]
      (for [[index card] (map-indexed vector (:hand board-half))]
        ^{:key (:id card)} [draw-card card index player board-atom is-owners-turn game-event-chan])]
     [:div.body
       [draw-hero (:hero board-half)]
       [:div.minion-container
        (for [minion (:minions board-half)]
          ^{:key (:id minion)} [draw-minion minion board board-atom is-owners-turn mouse-event-chan])]]]))

(defn draw-end-turn-button [board board-atom game-event-chan]
  [:div.end-turn {:on-click (fn [e]
                              (put! game-event-chan {:type :end-turn}))}
   "End Turn"])

(defn draw-combat-log-entry [board entry]
  [:div.log-entry
   (str
     (-> entry :target :name)
     " was attacked for "
     (- (-> entry :modifier :effect :health))
     " damage")])

(s/defn draw-combat-log [board]
  (let [combat-log (:combat-log board)]
    [:div.combat-log-viewport
     [:div.combat-log
     (for [entry combat-log]
       ^{:key (:id entry)} [draw-combat-log-entry board entry])]]))

(defn draw-board [board-atom game-event-chan mouse-event-chan]
  (let [board @board-atom]
    [:div.board
     ; TODO we pass like 300 args to all these functions; clean this up
     ; perhaps introduce a GameState schema that looks like {:game-event-chan a-chan :mouse-event-chan mouse-event-chan :board board}
     [draw-board-half board board-atom :player-1 (board :whose-turn) game-event-chan mouse-event-chan]
     [draw-board-half board board-atom :player-2 (board :whose-turn) game-event-chan mouse-event-chan]
     [draw-end-turn-button board board-atom game-event-chan mouse-event-chan]
     [draw-combat-log board]
     [:div.turn (pr-str (:whose-turn board)) (pr-str (:turn board))]]))

(defn draw-board-atom [board-atom]
  (let [game-event-chan (chan)
        mouse-event-chan (chan)]

    ; TODO - eventually implement click->click attacking
    (go-loop [origin-character-id nil]
      (let [msg (<! mouse-event-chan)]
        (condp = (msg :mouse-event-type)
          "dragstart" (recur (:character-id msg))
          "drop" (do
                   (when (not= (first (path-to-character (:board msg) origin-character-id))
                             (first (path-to-character (:board msg) (:character-id msg))))
                     (>! game-event-chan {:type :attack
                                        :origin-id origin-character-id
                                        :destination-id (:character-id msg)}))
                     (recur nil)))))

    (go-loop []
      (let [msg (<! game-event-chan)]
        (condp = (:type msg)
          :attack (swap! board-atom attack (msg :origin-id) (msg :destination-id))
          :play-card (swap! board-atom play-card (msg :player) (msg :index))
          :end-turn (swap! board-atom end-turn))
      (recur)))

    (r/render-component [draw-board board-atom game-event-chan mouse-event-chan]
                        (js/document.getElementById "content"))))
