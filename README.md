# My Visuals — клиентский мод для Fabric 1.21.4

Что умеет:
- **Правый Option** — открывает/закрывает меню (клавишу можно поменять в настройках управления).
- **Траектория**: лук (когда натягиваешь), заряженный арбалет, эндер-перл, снежок, яйцо, зелья. Зелёная линия + красный крестик в точке падения.
- **Помощь в строительстве**: держишь блок + зажата правая кнопка мыши + идёшь назад — блок ставится под ноги сам.
- **Погода**: обычная / ясно / дождь / снег / гроза. Видна только у тебя.

## Как собрать jar на Mac

1. Поставь Homebrew-пакеты (один раз):
   ```
   brew install gradle openjdk@21
   sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
   ```
2. Сгенерируй gradle wrapper (версия 8.12) в ПУСТОЙ папке и скопируй в проект:
   ```
   mkdir ~/wrapper-tmp && cd ~/wrapper-tmp
   touch settings.gradle
   gradle wrapper --gradle-version 8.12
   cp -R gradlew gradlew.bat gradle /путь/к/myvisuals/
   ```
3. Собери:
   ```
   cd /путь/к/myvisuals
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   ./gradlew build
   ```
4. Готовый мод: `build/libs/myvisuals-1.0.0.jar` (НЕ файл с `-sources`).
5. Положи jar в папку `mods` (в Т лаунчере: профиль → папка → mods). Нужен также **Fabric API для 1.21.4**.

Если сборка падает с ошибкой — пришли текст ошибки, поправлю.
