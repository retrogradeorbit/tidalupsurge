(ns ggj17.text
  (:require [infinitelives.pixi.sprite :as s]
            [infinitelives.pixi.events :as e]
            [infinitelives.utils.vec2 :as vec2]
            [infinitelives.utils.console :refer [log]]
            [infinitelives.utils.sound :as sound]
            [infinitelives.pixi.pixelfont :as pf]
            [ggj17.state :as state]
            [ggj17.wave :as wave])
  (:require-macros [infinitelives.pixi.macros :as m]
                   [ggj17.async :refer [go-while go-until-reload]]
                   [cljs.core.async.macros :refer [go]])
  )


(defn slide-text [text-string play-sound condition-fn layer y-val wait-frames speed]
  (go-while (condition-fn)
   (m/with-sprite layer
     [text (pf/make-text :small text-string
                         :scale 3
                         :x 0 :y y-val)]

     ;; Slide in

     (loop [fnum 0]
       (let [width (.-innerWidth js/window)
             height (.-innerHeight js/window)
             x-pos  (Math/pow (* speed 1.05) (- 50 fnum))]
         (s/set-x! text x-pos)

         (when (and play-sound (= 20 fnum)) (sound/play-sound :text-arrive 0.5 false))

         (<! (e/next-frame))
         (when (> x-pos 1)
           (recur (inc fnum)))))

     (<! (e/wait-frames wait-frames))

     ;; Slide out
     (loop [fnum 0]
       (let [width (.-innerWidth js/window)
             height (.-innerHeight js/window)
             x-pos  (- 0  (Math/pow (* speed 1.1) fnum))]
         (s/set-x! text x-pos)

         (when (and play-sound (= fnum 20)) (sound/play-sound :text-depart 0.5 false))

         (<! (e/next-frame))
         (when (> x-pos -1000)
           (recur (inc fnum))))))))

(defn slide-text-other [text-string play-sound condition-fn layer y-val wait-frames speed]
  (go-while (condition-fn)
   (m/with-sprite layer
     [text (pf/make-text :small text-string
                         :scale 3
                         :x 0 :y y-val)]

     ;; Slide in

     (loop [fnum 0]
       (let [width (.-innerWidth js/window)
             height (.-innerHeight js/window)
             x-pos  (Math/pow (* speed 1.05) (- 150 fnum))]
         (s/set-x! text x-pos)

         (when (and play-sound (= 20 fnum)) (sound/play-sound :text-arrive 0.5 false))

         (<! (e/next-frame))
         (when (> x-pos 1)
           (recur (inc fnum)))))

     (<! (e/wait-frames wait-frames))

     ;; Slide out
     (loop [fnum 0]
       (let [width (.-innerWidth js/window)
             height (.-innerHeight js/window)
             x-pos  (- 0  (Math/pow (* speed 1.1) fnum))]
         (s/set-x! text x-pos)

         (when (and play-sound (= fnum 20)) (sound/play-sound :text-depart 0.5 false))

         (<! (e/next-frame))
         (when (> x-pos -1000)
           (recur (inc fnum))))))))
