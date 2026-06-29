use tauri::Manager;

#[tauri::command]
async fn set_click_through(window: tauri::WebviewWindow, enabled: bool) -> Result<bool, String> {
  window
    .set_ignore_cursor_events(enabled)
    .map_err(|e| format!("Failed to set click through: {}", e))?;
  Ok(enabled)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
  tauri::Builder::default()
    .plugin(tauri_plugin_notification::init())
    .plugin(tauri_plugin_dialog::init())
    .plugin(tauri_plugin_fs::init())
    .setup(|app| {
      if cfg!(debug_assertions) {
        app.handle().plugin(
          tauri_plugin_log::Builder::default()
            .level(log::LevelFilter::Info)
            .build(),
        )?;
      }

      if let Some(window) = app.get_webview_window("main") {
        log::info!("window:created");
        log::info!("window:frameless=true");
        if window.is_always_on_top().unwrap_or(false) {
          log::info!("window:always-on-top-enabled");
        }
      }

      Ok(())
    })
    .invoke_handler(tauri::generate_handler![set_click_through])
    .run(tauri::generate_context!())
    .expect("error while running tauri application");
}
