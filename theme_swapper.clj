(ns theme-swapper
  (:require [babashka.fs :as fs]
            [babashka.cli :as cli]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [babashka.process :as process]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def cli-spec {:spec
               {:once {:coerce :boolean
                       :desc "Instructs the program to run once instead of looping."}}})
(def cli-opts (cli/parse-opts *command-line-args* cli-spec))
(def sh (comp process/check process/sh))
(def config-file (fs/file (.getParent (fs/file *file*)) "config.edn"))
(def config (or (when (fs/exists? config-file)
                  (edn/read (java.io.PushbackReader. (io/reader config-file))))
                {}))
(def wallpaper-directories (get config :wallpaper-directories []))
(def swap-interval (get config :swap-interval 300))

(defn picture? [file]
  (some-> file
          fs/extension
          string/lower-case
          #{"jpg" "jpeg" "png" "bmp" "webp"}))

(def wallpapers
  (fs/list-dirs wallpaper-directories picture?))

(def previous-wallpaper (atom nil))

(defn random-wallpaper []
  (when (seq wallpapers)
    (let [w (rand-nth wallpapers)]
      (if (and (> (count wallpapers) 1)
               (= w @previous-wallpaper))
        (recur)
        (reset! previous-wallpaper w)))))

(defn swap-theme []
  (when-let [wallpaper (random-wallpaper)]
    (try (sh ["matugen" "image" wallpaper])
         (sh ["killall" "-SIGUSR2" "waybar"])
         (catch Exception e
           (log/warn (.getMessage e))))))

(defn sleep []
  (Thread/sleep (* swap-interval 1000)))

;;;; Program loop.
(if (:once cli-opts)
  (swap-theme)
  (do
    (log/info "Launching the theme swapper. Swap interval:" swap-interval "seconds.")
    (when (:defer cli-opts) (sleep))
    (loop []
      (log/info "Swapping theme...")
      (swap-theme)
      (sleep)
      (recur))))
