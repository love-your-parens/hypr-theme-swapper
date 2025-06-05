(ns theme-swapper
  (:require [babashka.fs :as fs]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [babashka.process :as process]))

(def sh (comp process/check process/sh))
(def config-file (fs/file "./config.edn"))
(def config (or (when (fs/exists? config-file)
                  (clojure.edn/read (java.io.PushbackReader. (clojure.java.io/reader config-file))))
                {}))
(def wallpaper-directories (get config :wallpaper-directories []))
(def swap-interval (get config :swap-interval 300))

(defn picture? [file]
  (-> file
      fs/extension
      string/lower-case
      #{"jpg" "jpeg" "png" "bmp" "webp"}
      some?))

(def wallpapers
  (fs/list-dirs wallpaper-directories picture?))

(defn random-wallpaper
  []
  (when (seq wallpapers)
    (rand-nth wallpapers)))

(defn swap-theme []
  (when-let [wallpaper (random-wallpaper)]
    (try (sh (str "matugen image " wallpaper))
         (sh ["systemctl" "--user" "restart" "waybar"])
         (catch Exception e
           (log/warn (.getMessage e))))))

;;;; Program loop.
(log/info "Launching the theme swapper. Swap interval:" swap-interval "seconds.")
(loop []
  (log/info "Swapping theme...")
  (swap-theme)
  (Thread/sleep (* swap-interval 1000)))
