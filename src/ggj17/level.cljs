(ns ggj17.level
  (:require
   [infinitelives.pixi.events :as e]
   [ggj17.state :as state])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]
                   [ggj17.async :refer [go-while go-until-reload]]
                   [infinitelives.pixi.pixelfont :as pf]
                   ))

(defn level-thread []
  (go-while
   (state/playing?)
   (loop [fnum 0]
     (state/set-amp! (/ fnum 20))
     (<! (e/next-frame))
     (recur (inc fnum)))))
