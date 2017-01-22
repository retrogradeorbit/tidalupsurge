(ns ggj17.game
  (:require [infinitelives.pixi.canvas :as c]
            [infinitelives.pixi.resources :as r]
            [infinitelives.pixi.texture :as t]
            [infinitelives.pixi.tilemap :as tm]
            [infinitelives.pixi.sprite :as s]
            [infinitelives.pixi.pixelfont :as pf]
            [infinitelives.utils.events :as e]
            [infinitelives.utils.vec2 :as vec2]
            [infinitelives.utils.gamepad :as gp]
            [infinitelives.utils.pathfind :as path]
            [infinitelives.utils.console :refer [log]]
            [infinitelives.utils.sound :as sound]

            [ggj17.assets :as assets]
            [ggj17.explosion :as explosion]
            [ggj17.state :as state]
            [ggj17.level :as level]
            [ggj17.clouds :as clouds]
            [ggj17.popup :as popup]
            [ggj17.floaty :as floaty]
            [ggj17.wave :as wave]
            [ggj17.text :as text]
            [ggj17.consts :as consts]
            [ggj17.splash :as splash]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]
                   [ggj17.async :refer [go-while go-until-reload]]
                   [infinitelives.pixi.pixelfont :as pf]
                   )
  )

(def gravity (vec2/vec2 0 0.1))
(def max-speed 10)

(defn dead? []
  (or (e/is-pressed? :esc)
      false))

(defn clamp [val min max]
  (cond
    (< val min) min
    (> val max) max
    :default val
    )
  )

(defn health-display-thread []
  (go-while (state/playing?)
    (m/with-sprite :damage
      [health-text (pf/make-text :small (->> @state/state :health int (str "hull "))
                                 :scale 3
                                 :x -110 :y -20)]
      (loop [health (:health @state/state)]
        (<! (e/next-frame))
        (let [new-health (:health @state/state)]
          (when (not= new-health health)
            (.removeChildren health-text)
            (pf/change-text! health-text :small (str "hull " (int new-health))))
          (recur new-health)))))
  )


(defn score-display-thread []
  (go-while (state/playing?)
    (m/with-sprite :score
      [score-text (pf/make-text :small (->> @state/state :score int str)
                                 :scale 5
                                 :x 30 :y -30)]
      (loop [score (:score @state/state)]
        (<! (e/next-frame))
        (let [new-score (:score @state/state)]
          (when (not= new-score score)
            (.removeChildren score-text)
            (pf/change-text! score-text :small (str (int new-score))))
          (recur new-score)))))
  )


(defn jump-pressed? []
  (or
   (e/is-pressed? :space)
   (gp/button-pressed? 0 :a)
   (gp/button-pressed? 0 :b)
   (gp/button-pressed? 0 :x)
   (gp/button-pressed? 0 :y)))

(defn get-player-input-vec2 []
  (vec2/vec2 (or (gp/axis 0)
                 (cond (e/is-pressed? :left) -1
                       (e/is-pressed? :right) 1
                       :default 0) )
             (or (gp/axis 1)
                 (cond (e/is-pressed? :up) -1
                       (e/is-pressed? :down) 1
                       :default 0))))

(def jump-vec (vec2/vec2 0 -5))

(defn reset-hue-for [get-val set-val base]
  (go
    (loop []
      (let [hue (get-val set-val)]
        (set-val  (mod (+ hue 0.01) 1))
        (<! (e/next-frame))
        (when (not (and
                     (> (+ hue 0.02) base)
                     (< (- hue 0.02) base)
                     ))
          (recur))))))

(defn reset-hue []
  (reset-hue-for state/get-sky-hue state/set-sky-hue state/base-sky-colour)
  (reset-hue-for state/get-sea-hue state/set-sea-hue state/base-sea-colour))

(defn always-true []
  true)

(defn player-thread [player]
  (go-while
   (not (dead?))
   (state/set-amp! 1)
   (state/start-game!)

   (health-display-thread)
   (score-display-thread)
   (level/level-thread player)

   (sound/play-sound :game-start 0.5 false)

   (loop [fnum 0
          pos (vec2/vec2 0 0)
          vel (vec2/vec2 0 0)
          heading 0
          heading-delta 0
          last-frame-on-wave? false
          total-delta 0
          vel-x 0
          ]
                                        ;     (log "POS" pos)
     (let [
           {:keys [wave level-x]} @state/state
           {:keys [amp freq phase]} wave
           wave-x-pos (+ level-x phase)

           height (.-innerHeight js/window)
           width (.-innerWidth js/window)


           joy (get-player-input-vec2)
           joy-x (vec2/get-x joy)
           joy-y (vec2/get-y joy)
           half-width (/ (.-innerHeight js/window) 2)

           vel (vec2/add vel gravity)

;           pos2 (vec2/sub pos (vec2/vec2 level-x 0))
           pos2 (vec2/add pos vel)

           player-on-wave? (wave/on-wave? pos2 width height amp freq wave-x-pos)

           pos2 (vec2/add pos2 (if (and player-on-wave? (jump-pressed?)) jump-vec (vec2/zero)))

           constrained-pos (wave/constrain-pos
                            pos2
                            width height amp freq wave-x-pos)

           ;; now calculate the vel we pass through to next iter from our changed position

           vel (vec2/sub constrained-pos pos)

           old-heading heading
           heading (if player-on-wave?
                     (wave/wave-theta width height amp freq 0 ;wave-x-pos
                                      (- (/ (vec2/get-x pos2) 2) level-x)
                                      )
                     (+ heading heading-delta))

           heading-delta (if player-on-wave?
                           0
                           (+ heading-delta (* joy-y 0.01)))
           ;; damped heading delta back to 0
           heading-delta (if (neg? heading-delta)
                           (min 0 (+ heading-delta 0.001))
                           (max 0 (- heading-delta 0.001)))
           ]

       ;; landing
       (when (and (not last-frame-on-wave?) player-on-wave?)

         (splash/splash (vec2/vec2 level-x (vec2/get-y pos)))

         (let [flips (int (/ total-delta (* 2 Math/PI)))
               heading-diff (Math/abs (- heading old-heading))]
           (when (> heading-diff 0.5)
             (state/sub-damage! (* heading-diff 3)))

           ;; if landed and alive, popup
           (when (pos? (:health @state/state))

             (popup/popup! (vec2/add pos2 (vec2/vec2 0 30))
                           (str (consts/flip-score flips))
                           200)

             (text/slide-text (rand-nth (consts/flip-text flips)) false always-true :top-text 40 0 1.2)

             (sound/play-sound :water-splash 0.3 false)

             (when-let [sfx (consts/flip-sfx flips)]
               (sound/play-sound sfx 0.5 false))

             (state/add-score! (consts/flip-score flips))
             )


             ))

       (s/set-pos! player 0 (vec2/get-y constrained-pos))
       (s/set-rotation! player heading)

       (swap! state/state
              #(-> %
                   (update :level-x + vel-x)
                   (assoc :global-pos
                          (vec2/vec2 level-x
                                     (vec2/get-y pos)))
                   (assoc :pos pos)))

       (<! (e/next-frame))

;       (js/console.log "health: " (state/get-health) )

       (if (<= (state/get-health) 0)
         ;; die
         (do
           (state/set-health! 0)
           (reset-hue)
           (explosion/explosion player)
           (sound/play-sound :boom1 0.5 false)
           (<! (e/wait-frames 100))
           (m/with-sprite :ui
             [gameover (pf/make-text :small "Game Over" :scale 7)]
             (sound/play-sound :gameover 0.5 false)
             (<! (e/wait-frames 300)))
           (state/die!))

         ;; still living
         (recur (inc fnum)
                (vec2/add constrained-pos joy)
                vel
                heading
                heading-delta
                player-on-wave?

                (if player-on-wave?
                  0
                  (+ total-delta heading-delta))

                (let [input-speed (if player-on-wave? (/ joy-x 5) 0)
                      clamped-speed (clamp (+ vel-x input-speed) (- max-speed) max-speed)]
                  (if player-on-wave?
                    (* clamped-speed 0.99)
                    clamped-speed)
                  )))))))

