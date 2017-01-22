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
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]))

(defn wave-y-position [width height amp freq phase x]
  (*
     amp
     (Math/sin
         (+ phase
            (* freq x)))))

(defn wave-theta [width height amp freq phase x]
  (* 0.7
     (Math/atan
      (Math/cos
       (+ phase
          (* freq x))))))

(defn spawn-floaty! [xpos]
  (go
    (m/with-sprite :player
      [floaty (s/make-sprite :guy :scale 3 :x xpos :y 0)]
      (loop [xpos xpos]

        (let [{:keys [wave level-x]} @state/state
              {:keys [amp freq phase fnum]} wave
              wave-x-pos (+ phase level-x)]
          (s/set-pos!
           floaty
           xpos (wave-y-position
                 (.-innerWidth js/window)
                 (.-innerHeight js/window)
                 amp freq wave-x-pos xpos)))

        (<! (e/next-frame))
        (when (> xpos (- (+ 30 (/ (.-innerWidth js/window) 2)))) (recur (- xpos 3)))))))
