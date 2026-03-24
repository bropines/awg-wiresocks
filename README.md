# awg-wiresocks-android 🛡️

[![Add to Obtainium](https://img.shields.io/badge/Obtainium-Add_to_App-green?style=for-the-badge&logo=android)](obtainium://add/https://github.com/bropines/awg-wiresocks)

**awg-wiresocks-android** is a lightweight, high-performance Android application that brings the power of **AmneziaWG (AWG)** to your device without requiring root privileges or a system-wide VPN slot. 

By leveraging a custom-built Go engine based on `wireproxy-awg`, it creates a localized proxy environment (SOCKS5/HTTP) that bypasses strict network filters while keeping your connection stable and obfuscated.

## ✨ Key Features

* **Custom AWG Core:** Uses a specialized Go-based engine (`appctr`) for superior stability compared to standard implementations.
* **Protocol Obfuscation:** Full support for AmneziaWG parameters (`Jc`, `Jmin`, `Jmax`, `S1`, `S2`, `H1-H4`, `I1-I5`) to stay invisible to DPI.
* **Dual Proxy Support:** Simultaneously hosts SOCKS5 and HTTP proxies on configurable local ports.
* **Multi-Profile Management:** Import, export, and switch between multiple `.conf` profiles easily.
* **Network Persistence:** Built-in network monitor that automatically restores the tunnel when switching between Wi-Fi and mobile data.
* **Quick Access:** Toggle your proxy directly from the Android Quick Settings (Tiles).
* **Engine Logs:** Real-time logging for debugging and connection monitoring.

## 🛠️ Tech Stack

* **Frontend:** Kotlin & Jetpack Compose (Modern Material 3 UI).
* **Backend/Engine:** Go (Golang) compiled to AAR via `gomobile`.
* **Network Layer:** Based on `wireproxy-awg` and `amneziawg-go`.

## 🚀 How to Use

1.  **Import:** Load your `.conf` file (standard WireGuard or AmneziaWG format).
2.  **Configure:** Set your desired SOCKS5 or HTTP ports in the Config tab.
3.  **Start:** Hit the activation button on the Home screen or use the Quick Settings Tile.
4.  **Connect:** Configure your browser or apps to use `127.0.0.1` with your chosen port.

## 🏗️ Building from Source

### Prerequisites
* Android SDK & NDK
* Go (1.25+)
* `gomobile` installed and initialized

### Build Steps
1.  **Compile Go Core:**
    ```bash
    cd appctr
    ./build.sh
    ```
2.  **Build Android App:**
    ```bash
    ./gradlew assembleRelease
    ```

## 📜 Acknowledgments & Credits

* **Application Developer:** [bropines](https://github.com/bropines) (pinus / еж)
* **Core Logic:** Based on [wireproxy-awg](https://github.com/artem-russkikh/wireproxy-awg) by Artem Russkikh.
* **Original Tool:** Derived from the original [wireproxy](https://github.com/pufferffish/wireproxy) by Wind Wong.

## ⚖️ License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details. 
The core engine components are subject to their respective **ISC** and **MIT** licenses.
