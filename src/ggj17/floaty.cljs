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
  (let [wave-x-pos (+ phase level-x)]
    (log "wave-y-pos" (.-innerWidth js/window)
      (.-innerHeight js/window)
      amp freq wave-x-pos xpos )
    (s/set-pos!
     floaty
                                        ;xpos
     (- xpos (:level-x @state/state))
     
     (wave/wave-y-position
      (.-innerWidth js/window)
      (.-innerHeight js/window)
      amp freq wave-x-pos (- xpos wave-x-pos)
      )))
  )

(defn spawn-floaty! [xpos]
  (let [start-level-x (:level-x @state/state)]
    (go
      (m/with-sprite :player
        [floaty (s/make-sprite :guy :scale 3 :x xpos :y 0)]
        (loop []

          (log "LEVEL X:" (:level-x @state/state))
          
          (let [{:keys [wave level-x]} @state/state
                {:keys [amp freq phase fnum]} wave]
            (setpos floaty level-x amp freq phase fnum xpos)
            )

          (<! (e/next-frame))
          (when (> (- xpos (:level-x @state/state)) (- (+ 30 (/ (.-innerWidth js/window) 2))))
            (recur)))))))
