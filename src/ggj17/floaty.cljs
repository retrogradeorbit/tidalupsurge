(ns ggj17.floaty
  (:require [infinitelives.utils.vec2 :as vec2]
            [infinitelives.utils.events :as e]
            [infinitelives.utils.boid :as b]
            [infinitelives.utils.math :as math]
            [infinitelives.utils.spatial :as spatial]
            [infinitelives.utils.sound :as sound]
            [infinitelives.utils.console :refer [log]]
            [infinitelives.pixi.sprite :as s]
            [ggj17.state :as state]
            [ggj17.wave :as wave]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]))

(defn setpos [floaty level-x amp freq phase fnum xpos]
  (let [wave-x-pos (+ phase level-x)
        new-x (- xpos (:level-x @state/state))
        new-y (wave/wave-y-position
               (.-innerWidth js/window)
               (.-innerHeight js/window)
               amp freq wave-x-pos (- xpos wave-x-pos)
               )
        ]
    (s/set-pos! floaty new-x new-y)
    (vec2/vec2 new-x new-y)))

(defn spawn-floaty! [player xpos]
  (let [start-level-x (:level-x @state/state)]
    (go
      (m/with-sprite :player
        [floaty (s/make-sprite :guy :scale 3 :x xpos :y 0)]
        (loop []
          (let [{:keys [wave level-x]} @state/state
                {:keys [amp freq phase fnum]} wave
                new-pos (setpos floaty level-x amp freq phase fnum xpos)]
            
            (<! (e/next-frame))
            (when (> (- xpos (:level-x @state/state)) (- (+ 30 (/ (.-innerWidth js/window) 2))))
              ;; not off screen to left
              (if (<
                   (vec2/magnitude-squared
                    (vec2/sub (s/get-pos player)
                              (s/get-pos floaty)))
                   200)
                
                ;; dude hit
                (do
                  (s/set-texture! floaty :splat)
                  (sound/play-sound :zap2 0.5 false)
                  (state/sub-damage! 3)
                  (loop [count 100]
                    (let [{:keys [wave level-x]} @state/state
                          {:keys [amp freq phase fnum]} wave
                          new-pos (setpos floaty level-x amp freq phase fnum xpos)]
                      (<! (e/next-frame))
                      (when (pos? count)
                        (recur (dec count))))))                

                ;; dude not hit
                (recur)))))))))
