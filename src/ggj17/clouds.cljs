(ns ggj17.clouds
  (:require [infinitelives.utils.math :as math]
            [infinitelives.utils.vec2 :as vec2]
            [infinitelives.utils.console :refer [log]]
            [infinitelives.pixi.events :as e]
            [infinitelives.pixi.sprite :as s]
            [infinitelives.pixi.resources :as r]
            [ggj17.state :as state]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]))

(def num-clouds 30)

(def cloud-choice [:cloud])
(def cloud-set
  (sort-by :z
           (map (fn [n]
                  (let [depth (math/rand-between 0 (dec (count cloud-choice)))]
                    {:x (math/rand-between 0 2048)
                     :y (math/rand-between 0 2048)
                     :z (+ 2 depth (rand))
                     :depth depth})) (range num-clouds))))
(def scale 3)
(def edge-gutter (* 128 scale))
(def cloud-y -400)
(def cloud-layer-height 250)


(defn get-sprites []
  (for [{:keys [x y z depth]} cloud-set]
    (s/make-sprite
     (rand-nth cloud-choice)
     :x (* scale x)
     :y (* scale y)
     :scale scale
     ;;:alpha 0.5
     )))

(defn set-cloud-positions! [clouds [xp yp]]
  (let [w (+ edge-gutter (.-innerWidth js/window))
        h cloud-layer-height
        hw (/ w 2)
        hh (/ h 2)]
    (doall
     (map
      (fn [{:keys [x y z] :as old} sprite]
        (s/set-pos! sprite
                    (- (mod (+ (* 4 x) (* 0.1 (- xp) z)) w) hw)
                    (+ (- (mod (+ (* 4 y) (* 0.1 (- yp) z)) h) hh) cloud-y)))
      cloud-set
      clouds))))

(defn cloud-thread [clouds]
  (go
    (loop [c 0]
      (<! (e/next-frame))
      (set-cloud-positions! clouds [(-> state/state deref :level-x) 0])
      (recur (inc c)))))
