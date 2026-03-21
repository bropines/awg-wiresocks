# awg-wiresocks-android 🛡️

**awg-wiresocks-android** is a lightweight, high-performance Android application that brings the power of **AmneziaWG (AWG)** to your device without requiring root privileges or a system-wide VPN slot. 

By leveraging a custom-built Go engine based on `wireproxy-awg`, it creates a localized proxy environment (SOCKS5/HTTP) that bypasses strict network filters while keeping your connection stable and obfuscated.



## ✨ Key Features

* [cite_start]**Custom AWG Core:** Uses a specialized Go-based engine (`appctr`) for superior stability compared to standard implementations[cite: 213, 341].
* [cite_start]**Protocol Obfuscation:** Full support for AmneziaWG parameters (`Jc`, `Jmin`, `Jmax`, `S1`, `S2`, `H1-H4`, `I1-I5`) to stay invisible to DPI[cite: 40, 41, 342, 343].
* [cite_start]**Dual Proxy Support:** Simultaneously hosts SOCKS5 and HTTP proxies on configurable local ports[cite: 337, 338, 349].
* [cite_start]**Multi-Profile Management:** Import, export, and switch between multiple `.conf` profiles easily[cite: 221, 222].
* [cite_start]**Network Persistence:** Built-in network monitor that automatically restores the tunnel when switching between Wi-Fi and mobile data[cite: 218].
* [cite_start]**Quick Access:** Toggle your proxy directly from the Android Quick Settings (Tiles)[cite: 222, 238].
* [cite_start]**Engine Logs:** Real-time logging for debugging and connection monitoring[cite: 312, 321].

## 🛠️ Tech Stack

* [cite_start]**Frontend:** Kotlin & Jetpack Compose (Modern Material 3 UI)[cite: 214, 217, 323].
* [cite_start]**Backend/Engine:** Go (Golang) compiled to AAR via `gomobile`[cite: 214, 351, 352].
* [cite_start]**Network Layer:** Based on `wireproxy-awg` and `amneziawg-go`[cite: 128, 134, 341].

## 🚀 How to Use

1.  [cite_start]**Import:** Load your `.conf` file (standard WireGuard or AmneziaWG format)[cite: 247, 248].
2.  [cite_start]**Configure:** Set your desired SOCKS5 or HTTP ports in the Config tab[cite: 263, 264].
3.  [cite_start]**Start:** Hit the activation button on the Home screen or use the Quick Settings Tile[cite: 238, 294].
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

* **Application Developer:** [bropines](https://github.com/bropines) (pinus/еж)
* [cite_start]**Core Logic:** Based on [wireproxy-awg](https://github.com/artem-russkikh/wireproxy-awg) by Artem Russkikh[cite: 134, 341].
* [cite_start]**Original Tool:** Derived from the original [wireproxy](https://github.com/pufferffish/wireproxy) by Wind Wong[cite: 128, 134].

## ⚖️ License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details. 
The core engine components are subject to their respective **ISC** and **MIT** licenses.