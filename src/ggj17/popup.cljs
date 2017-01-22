(ns ggj17.popup
  (:require [infinitelives.utils.vec2 :as vec2]
            [infinitelives.utils.events :as e]
            [infinitelives.utils.boid :as b]
            [infinitelives.pixi.pixelfont :as pf]
            [infinitelives.utils.math :as math]
            [infinitelives.utils.console :refer [log]]
            [infinitelives.utils.spatial :as spatial]
            [infinitelives.pixi.sprite :as s]
            [ggj17.state :as state]
            [ggj17.explosion :as explosion]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]))

(defn popup! [pos score life]
  (go
    (m/with-sprite :ui
      [score-text (pf/make-text :small (str score) :scale 3)]
      (loop [life life
             pos pos
             scale 3
             alpha 1.0]
        (s/set-pos! score-text pos)
        (s/set-scale! score-text scale)
        (s/set-alpha! score-text alpha)
        (<! (e/next-frame))
        (when (pos? life)
          (recur (dec life)
                 (vec2/add pos (vec2/vec2 0 2))
                 (+ scale 0.04)
                 (max 0 (- alpha 0.01))))))))
