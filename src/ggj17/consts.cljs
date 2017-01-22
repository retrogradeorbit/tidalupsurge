(ns ggj17.consts
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
            [ggj17.floaty :as floaty])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]
                   [ggj17.async :refer [go-while go-until-reload]]
                   [infinitelives.pixi.pixelfont :as pf]
                   ))

(def flip-text
  {
   -3 ["sick triple backflip!"]
   -2 ["double backflip"]
   -1 ["backward flip!"
       "reverse roll"
       "do a barrel roll"
       "backflip"]
   0 ["jump landed"
      "splash down"]
   1 ["sick flip bro!"
      "a full flip"
      "forward roll"]
   2 ["double flip!"
      "720!"
      "double forward roll"]
   3 ["triple flippage"
      "three full loops"
      "you're a loop master"]
   })

(def flip-score
  {
   -3 500
   -2 200
   -1 50
   0 5
   1 50
   2 200
   3 500
   })

(def flip-sfx
  {
   -3 :land1
   -2 :land1
   -1 :land1
   1 :land1
   2 :land1
   3 :land1
   })
