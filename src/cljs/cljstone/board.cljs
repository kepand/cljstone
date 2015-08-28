(ns cljstone.board
  (:require [schema.core :as s]
            [cljstone.hero :as hero])
  (:use [cljstone.minion :only [Minion MinionSchematic Modifier get-attack make-minion]]))

(def BoardHalf
  {:index s/Int ; xxx is this used anywhere?
   :hero hero/Hero
   :minions [Minion]})

(def Character
  {:id s/Int
   :base-health s/Int
   :base-attack s/Int
   :modifiers [Modifier]
   s/Any s/Any})

(def Board
  {:half-1 BoardHalf
   :half-2 BoardHalf})

(s/defn make-board :- Board
  [hero-1 :- hero/Hero
   hero-2 :- hero/Hero]
  {:half-1 {:index 0 :hero hero-1 :minions []}
   :half-2 {:index 1 :hero hero-2 :minions []}})

(s/defn summon-minion :- Board
  [board :- Board
   which-board-half :- (s/enum :half-1 :half-2)
   schematic :- MinionSchematic
   id :- s/Int]
  (let [minion (make-minion schematic id)]
    (-> board
        (update-in [which-board-half :minions] conj minion))))

(s/defn attack :- [Character]
  [character-1 :- Character
   character-2 :- Character]
  (let [create-attack-modifier (fn [c1 c2]
                                 {:type attack
                                  :name nil
                                  :effects {:health (- (get-attack c1))}})]
    [(update-in character-1 [:modifiers] conj (create-attack-modifier character-2 character-1))
     (update-in character-2 [:modifiers] conj (create-attack-modifier character-1 character-2))]))


; xxxxx fix function names
(s/defn perform-attack :- Board
  [board :- Board
   character-id-1 :- s/Str
   character-id-2 :- s/Str]
  (let [character-1 ((board :characters-by-id) character-id-1)
        character-2 ((board :characters-by-id) character-id-2)
        [attacked-character-1 attacked-character-2] (attack character-1 character-2)]
    (-> board
        (assoc-in [:characters-by-id character-id-1] attacked-character-1)
        (assoc-in [:characters-by-id character-id-2] attacked-character-2))))
