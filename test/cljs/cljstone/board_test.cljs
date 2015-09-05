(ns cljstone.board-test
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [cljstone.card :as c]
            [cljstone.hero :as h]
            [cljstone.minion :as m]
            [schema.core :as s])
  (:use [cljstone.board :only [find-a-dead-character-in-board path-to-character make-board play-card remove-minion attack BoardHalf]]
        [cljstone.character :only [get-next-character-id]]
        [schema.test :only [validate-schemas]]))

(use-fixtures :once validate-schemas)

(def hero-1 (h/make-hero "Jaina" :mage (get-next-character-id)))
(def hero-2 (h/make-hero "Thrall" :shaman (get-next-character-id)))
(def board @(make-board hero-1 hero-2))

(deftest find-dead-character
  (testing "no dead characters"
    (is (= (find-a-dead-character-in-board board) nil)))

  (let [card (first (get-in board [:player-1 :hand]))
        board (-> board
                  (play-card :player-1 0)
                  (assoc-in [:player-1 :minions 0 :base-health] 0))]

    (testing "one dead character"
      (is (= (:name card)
             (:name (find-a-dead-character-in-board board)))))

    (let [board (-> board
                    (play-card :player-1 0)
                    (assoc-in [:player-1 :minions 1 :base-health] 0))
          first-minion (get-in board [:player-1 :minions 0])]
    ; xxx is left-to-right the correct order to seek dead minions? probably not, right?
    ; should be sorting by id, not board position - update this test when we implement deathrattles (and playing a minion at a position) and it starts mattering
    (testing "if there are two dead characters, we should get the first"
      (is (= (:base-health first-minion) 0))
      (is (= (get-in board [:player-1 :minions 1 :base-health]) 0))
      (is (= (:id (find-a-dead-character-in-board board))
             (:id first-minion)))))))

(deftest removing-minions
  (let [board (-> board
                  (play-card :player-1 0)
                  (play-card :player-2 0)
                  (play-card :player-1 0))
        player-1-minions (get-in board [:player-1 :minions])]
  (is (= (get-in (remove-minion board (:id (nth player-1-minions 1)))
                 [:player-1 :minions])
         (subvec player-1-minions 0 1)))))

(deftest finding-paths
  (testing "looking up heroes"
    (is (= (path-to-character board (:id hero-1))
           [:player-1 :hero])
        (= (path-to-character board (:id hero-2))
           [:player-2 :hero])))

  (testing "looking up minions"
   (let [board (-> board
                   (play-card :player-1 0)
                   (play-card :player-1 0)
                   (play-card :player-2 0))
         minion-to-find (get-in board [:player-1 :minions 1])]
     (is (= (path-to-character board (:id minion-to-find))
            [:player-1 :minions 1])))))

(deftest grim-reaper
  ; TODO test for hero death

  (let [board (make-board hero-1 hero-2)]
    (swap! board play-card :player-1 0)
    (swap! board play-card :player-1 0)
    (swap! board play-card :player-1 0)
    (swap! board play-card :player-2 0)
    (swap! board play-card :player-2 0)

    (testing "minion death"
      (let [test-minion (get-in @board [:player-1 :minions 1])]
        (is (not (= (path-to-character @board (:id test-minion))
                    nil)))

        ; oh no, our minion has been attacked for its full amount of HP!
        (swap! board update-in [:player-1 :minions 1 :modifiers] conj {:type :attack
                                                                       :name nil
                                                                       :effect {:health (- (:base-health test-minion))}})

        ; the :grim-reaper watch should have automatically cleaned it up, and it should no longer exist in the board.
        ; TODO: test deathrattles.
        (is (= (path-to-character @board (:id test-minion))
               nil))))))

(s/defn make-test-board-half :- BoardHalf
  [{:keys [hero hand deck minions] :or {hand [] deck [] minions []}}]
  {:hero hero :hand hand :deck deck :minions minions})

(deftest attacking
  (testing "two minions attacking each other"
    (let [board {:player-1 (make-test-board-half {:hero hero-1 :minions [(m/make-minion (:boulderfist-ogre m/all-minions) 123)]})
                 :player-2 (make-test-board-half {:hero hero-2 :minions [(m/make-minion (:war-golem m/all-minions) 234)]})}
          post-attack-board (attack board 123 234)]
      ; ogre dies, war golem survives with 1 health
      (is (= (m/get-health (get-in post-attack-board [:player-1 :minions 0]))
             0))
      (is (= (m/get-health (get-in post-attack-board [:player-2 :minions 0]))
             1)))))

(deftest playing-cards
  (testing "playing a minion"
    (let [card (c/minion-schematic->card (:wisp m/all-minions))
          board {:player-1 (make-test-board-half {:hero hero-1 :hand [card]})
                 :player-2 (make-test-board-half {:hero hero-2})}
          post-play-board (play-card board :player-1 0)]
      (is (= (get-in post-play-board [:player-1 :minions 0 :name])
             "Wisp")))))
