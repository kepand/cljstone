(ns cljstone.board-test
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [cljstone.minion :as m]
            [schema.core :as s])
  (:use [cljstone.bestiary :only [all-minions]]
        [cljstone.board :only [path-to-character end-turn run-continuation play-card get-mana]]
        [cljstone.board-mode :only [DefaultMode]]
        [cljstone.character :only [can-attack]]
        [cljstone.combat :only [attack]]
        [cljstone.test-helpers :only [hero-1 hero-2 fresh-board three-minions-per-player-board]]
        [schema.test :only [validate-schemas]]))

(use-fixtures :once validate-schemas)

(deftest finding-paths
  (testing "looking up heroes"
    (is (= (path-to-character fresh-board (:id hero-1))
           [:player-1 :hero])
        (= (path-to-character fresh-board (:id hero-2))
           [:player-2 :hero])))

  (testing "looking up minions"
   (let [minion-to-find (get-in three-minions-per-player-board [:player-1 :minions 1])]
     (is (= (path-to-character three-minions-per-player-board (:id minion-to-find))
            [:player-1 :minions 1])))))

(deftest turns
  (testing "turns existing"
    (is (= (:turn fresh-board) 0))

    (let [board (assoc fresh-board :whose-turn :player-1)
          board (end-turn board)]
      (is (= (:turn board 1)))
      (is (= (:whose-turn board) :player-2))))

  (testing "resetting minions' number of attacks this turn"
    (let [board (-> fresh-board
                    (assoc :whose-turn :player-1)
                    (assoc-in [:player-1 :minions 0] (m/make-minion (:river-crocilisk all-minions) 123))
                    (assoc-in [:player-2 :minions 0] (m/make-minion (:river-crocilisk all-minions) 234)))]
      ; player 1 and player 2 each have a river croc.
      (is (= true (can-attack (get-in board [:player-1 :minions 0]))))

      (let [board (attack board 123 234)]
        ; player 1's croc attacks player 2's croc; it can only attack once per turn, so it can't attack again.
        (is (= false (can-attack (get-in board [:player-1 :minions 0]))))

        (let [board (end-turn board)]
          ; once player 1 hits "end turn", though, the croc can attack again the next time it's p1's turn.
          (is (= true (can-attack (get-in board [:player-1 :minions 0])))))))))

(deftest running-continuations
  (let [board (assoc fresh-board :mode {:type :targeting
                                        :targets []
                                        :continuation (fn [board b c]
                                                        (is (= b 123))
                                                        (is (= c 234))
                                                        (assoc board :mode DefaultMode))})]
    (is (= (run-continuation board 123 234)
           fresh-board)))

  ; Can't call run-continuation on a DefaultModed board.
  (is (thrown? js/Error (run-continuation fresh-board 123))))

(deftest getting-mana
  (let [board (-> fresh-board
                  (assoc-in [:player-1 :hand 0 :mana-cost] 4)
                  (assoc-in [:player-1 :mana] 7)
                  (play-card :player-1 0))]
    (is (= {:max 7 :actual 3}
           (get-mana (board :player-1))))))

(deftest playing-cards
  ; god, i'd love to use a generator here
  (testing "when a card gets played, its :effect function gets run and the result is returned"
    (let [test-card {:type :minion
                     :name "foo"
                     :mana-cost 1
                     :id 123
                     :class :warrior
                     :effect (fn [board player new-hand]
                               (assoc-in board [player :hand] new-hand))}
          board (assoc-in fresh-board [:player-1 :hand 0] test-card)
          old-hand (get-in board [:player-1 :hand])]
      (is (= (play-card board :player-1 0)
             (-> fresh-board
                 (assoc-in [:player-1 :hand] (subvec old-hand 1))
                 (update-in [:player-1 :mana-modifiers] conj -1)))))))
