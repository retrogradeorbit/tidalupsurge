(ns ggj17.splash
  (:require [infinitelives.pixi.sprite :as s]
            [infinitelives.pixi.events :as e]
            [infinitelives.utils.vec2 :as vec2]
            [infinitelives.utils.console :refer [log]]
            [ggj17.state :as state])
  (:require-macros [infinitelives.pixi.macros :as m]
                   [cljs.core.async.macros :refer [go]])
  )

(def splash-frames [:splash-1
                    :splash-2
                    :splash-3
                    :splash-4
                    :splash-5
                    :splash-6])

(def splash-speed 4)

(def splash-scale 4)

(defn splash [entity]
  ;(sound/play-sound (keyword (str "explode-" (rand-int 10))) 0.5 false)
  (let [frameset splash-frames]
    (go
      (let [initial-pos (s/get-pos entity)
            x (vec2/get-x initial-pos)
            y (vec2/get-y initial-pos)
            frames (count frameset)
            total-frames (* frames splash-speed)
            ]
        (m/with-sprite :player
          [splash (s/make-sprite (first frameset)
                                    :scale splash-scale :x x :y y
                                    )]
          (loop [n 0
                 pos initial-pos]
            (s/set-texture! splash (get frameset (int (/ n splash-speed)) (last frameset)))
            (s/set-pos! splash pos)

            (<! (e/next-frame))

            (when (< n total-frames)
              (recur (inc n) pos))))))))
