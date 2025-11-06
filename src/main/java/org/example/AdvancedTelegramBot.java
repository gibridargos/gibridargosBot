    package org.example;

    import org.telegram.telegrambots.bots.TelegramLongPollingBot;
    import org.telegram.telegrambots.meta.TelegramBotsApi;
    import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
    import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
    import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
    import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
    import org.telegram.telegrambots.meta.api.objects.Update;
    import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
    import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
    import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
    import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
    import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
    import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
    import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
    import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

    import java.util.*;

    public class AdvancedTelegramBot extends TelegramLongPollingBot {

        private final DatabaseService db = new DatabaseService();
        private final AdminService adminService = new AdminService(db);
        public final Map<Long, Boolean> awaitingAdminPassword = new HashMap<>();

        // Majburiy kanallar ro‘yxati
        private final List<String> requiredChannels = List.of(
                "@argos_testlarim",
                "@gibridtest",
                "@gibridtesthamshira"
        );

        @Override
        public String getBotUsername() {
                return "@ARGOSGIBRIDTESTBOT"; // @sizning_bot_username
        }

        @Override
        public String getBotToken() {
            return "8587886208:AAH2g1PJdEtis0vYbel-RlbCoaVQ8T-tUeE"; // tokenni bu yerga joylang
        }

        @Override
        public void onUpdateReceived(Update update) {
            try {
                // admin logikasi
                if (adminService.handleAdminCommands(update, this)) return;

                // foydalanuvchi xabarlari
                if (update.hasMessage() && update.getMessage().hasText()) {
                    Long chatId = update.getMessage().getChatId();
                    String text = update.getMessage().getText();
                    String username = update.getMessage().getFrom().getUserName();
                    String firstName = update.getMessage().getFrom().getFirstName();

                    db.saveUser(chatId, username, firstName);

                    if (text.equals("/start")) {
                        sendStartWithInlineChannels(chatId);
                    } else if (text.equals("\uD83E\uDDE0 Тест ишлаш")) {
                        sendWebApp(chatId);
                    }
                }

                // inline callback tugma bosilganda
                if (update.hasCallbackQuery()) {
                    String data = update.getCallbackQuery().getData();
                    Long chatId = update.getCallbackQuery().getMessage().getChatId();
                    Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

                    if (data.equals("check_subs")) {
                        List<String> unsubscribed = getUnsubscribedChannels(chatId);
                        if (unsubscribed.isEmpty()) {
                            sendAccessGranted(chatId);
                        } else {
                            updateUnsubscribedChannels(chatId, messageId, unsubscribed);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // faqat obuna bo‘lmagan kanallarni qaytaradi
        private List<String> getUnsubscribedChannels(Long chatId) {
            List<String> notJoined = new ArrayList<>();
            for (String channel : requiredChannels) {
                try {
                    GetChatMember chatMember = new GetChatMember(channel, chatId);
                    ChatMember member = execute(chatMember);
                    String status = member.getStatus();

                    if (status.equals("left") || status.equals("kicked")) {
                        notJoined.add(channel);
                    }
                } catch (Exception e) {
                    notJoined.add(channel); // agar xatolik bo‘lsa, kanalni obuna bo‘lmagan deb hisoblaymiz
                }
            }
            return notJoined;
        }

        // start bosilganda barcha kanallarni inline tarzda chiqaradi
        private void sendStartWithInlineChannels(Long chatId) throws Exception {
            String text = " \uD83D\uDCAC Ассалому алайкум. Ботимизга хуш келибсиз.\n" +
                    "\n" +
                    "✅ СИНОВ ТЕСТЛАРИ СИЗНИ КУТМОКДА!!!\n" +
                    "\n" +
                    "- Турли-туман тустлар\n" +
                    "- Билимингизни синаш имконияти\n" +
                    "- Янги билимларга эга булиш\n" +
                    "- Хамкасблар билан ракобатлашиш\n" +
                    "\n" +
                    "\uD83D\uDD3B Тест ишлаш учун канал ва гурухимизга обуна булинг.";

            // Kanal username -> Siz belgilagan nom map
            Map<String, String> channelNamesMap = new HashMap<>();
            channelNamesMap.put("@argos_testlarim", "АРГОС ТЕСТЛАРИ КАНАЛИ");
            channelNamesMap.put("@gibridtest", "ГИБРИД ТЕСТ УАШ КАНАЛИ");
            channelNamesMap.put("@gibridtesthamshira", "ГИБРИД ТЕСТ ХАМШИРА КАНАЛИ");

            List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
            for (String ch : requiredChannels) {
                String displayName = channelNamesMap.getOrDefault(ch, ch); // map dagi nom yoki username
                InlineKeyboardButton btn = new InlineKeyboardButton("📢 " + displayName);
                btn.setUrl("https://t.me/" + ch.substring(1)); // kanal linki
                buttons.add(List.of(btn));
            }

            // Obunani tekshirish tugmasi
            InlineKeyboardButton checkBtn = new InlineKeyboardButton("✅ Обунани текшириш");
            checkBtn.setCallbackData("check_subs");
            buttons.add(List.of(checkBtn));

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup(buttons);

            SendMessage msg = new SendMessage(chatId.toString(), text);
            msg.setReplyMarkup(markup);
            execute(msg);
        }

        // 🔄 Obuna bo‘lmagan kanallarni faqat yangilaydi (edit message orqali)
        private void updateUnsubscribedChannels(Long chatId, Integer messageId, List<String> unsubscribed) {
            try {
                // 🔤 Chiroyli matn
                StringBuilder sb = new StringBuilder("\uD83D\uDE43 Сиз канал ёки гуруҳга обуна бўлмагансиз!\n\n");
                sb.append("\uD83C\uDFAF Қуйидаги тугмалар орқали обуна бўлинг ва яна уриниб кўринг:\n\n");

                // 📋 Kanal nomlari xaritasi
                Map<String, String> channelNamesMap = new HashMap<>();
                channelNamesMap.put("@argos_testlarim", "АРГОС ТЕСТЛАРИ КАНАЛИ");
                channelNamesMap.put("@gibridtest", "ГИБРИД ТЕСТ УАШ КАНАЛИ");
                channelNamesMap.put("@gibridtesthamshira", "ГИБРИД ТЕСТ ХАМШИРА КАНАЛИ");

                // 🧩 Inline tugmalar
                List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

                for (String ch : unsubscribed) {
                    String displayName = channelNamesMap.getOrDefault(ch, ch);
                    sb.append("👉 ").append(displayName).append("\n");

                    InlineKeyboardButton btn = new InlineKeyboardButton("📢 " + displayName);
                    btn.setUrl("https://t.me/" + ch.substring(1));
                    buttons.add(List.of(btn));
                }

                sb.append("\n🔄 Обуна бўлгач, қайта текширинг:");

                InlineKeyboardButton checkBtn = new InlineKeyboardButton("✅ Қайта текшириш");
                checkBtn.setCallbackData("check_subs");
                buttons.add(List.of(checkBtn));

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup(buttons);

                // 📝 Xabarni tahrirlash
                EditMessageText editMsg = new EditMessageText();
                editMsg.setChatId(chatId.toString());
                editMsg.setMessageId(messageId);
                editMsg.setText(sb.toString());
                editMsg.setReplyMarkup(markup);

                try {
                    execute(editMsg);
                } catch (Exception e) {
                    // ⚠️ "message is not modified" xatosini e’tiborga olmaslik
                    if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                        System.out.println("⚠️ Xabar o‘zgarmagani uchun tahrir qilinmadi.");
                    } else {
                        e.printStackTrace();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }



        // barcha kanalga obuna bo‘lsa — test menyusi chiqadi
        private void sendAccessGranted(Long chatId) throws Exception {
            SendMessage msg = new SendMessage(chatId.toString(),
                    "\uD83C\uDF89 Ажойиб! Сиз барча каналларга обуна бўлгансиз.\n" +
                            "Энди тестни бошлашингиз мумкин \uD83D\uDC47");

            KeyboardButton testBtn = new KeyboardButton("\uD83E\uDDE0 Тест ишлаш");
            KeyboardRow row = new KeyboardRow();
            row.add(testBtn);

            ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
            markup.setResizeKeyboard(true);
            msg.setReplyMarkup(markup);

            execute(msg);
        }

        // test web-app tugmasi
        private void sendWebApp(Long chatId) throws Exception {
            SendMessage msg = new SendMessage(chatId.toString(), "\uD83C\uDF10 Тестни бошлаш учун қуйидаги тугмани босинг:");
            InlineKeyboardButton webButton = new InlineKeyboardButton("\uD83D\uDE80 Тестни бошлаш");
            webButton.setWebApp(new WebAppInfo("https://gibridargos.github.io/gibridargos/"));

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(List.of(webButton)));
            msg.setReplyMarkup(markup);
            execute(msg);
        }

        public void executeSafely(SendMessage message) {
            try {
                execute(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void executeSafely(SendPhoto photo) {
            try {
                execute(photo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        public void showAdminMenu(Long chatId) {
            KeyboardButton sendMsgBtn = new KeyboardButton("📢 Habar yuborish");
            KeyboardRow row = new KeyboardRow();
            row.add(sendMsgBtn);

            ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
            markup.setResizeKeyboard(true);

            SendMessage msg = new SendMessage(chatId.toString(), "🔐 Admin menyusi:");
            msg.setReplyMarkup(markup);
            executeSafely(msg);
        }

    }
