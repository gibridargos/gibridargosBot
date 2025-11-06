package org.example;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.sql.ResultSet;
import java.util.*;

public class AdminService {
    private final DatabaseService db;
    private final String ADMIN_PASSWORD = "secret123";
    private final Long ADMIN_ID = 7292882679L;

    // Admin holatlarini saqlaymiz
    private final Map<Long, Boolean> awaitingBroadcast = new HashMap<>();
    private final Map<Long, Boolean> awaitingPhoto = new HashMap<>();
    private final Map<Long, String> photoFileId = new HashMap<>();

    public AdminService(DatabaseService db) {
        this.db = db;
    }

    public boolean handleAdminCommands(Update update, AdvancedTelegramBot bot) {
        try {
            if (update == null) return false;
            Long chatId = getChatId(update);
            if (chatId == null) return false;

            String text = update.hasMessage() && update.getMessage().hasText()
                    ? update.getMessage().getText()
                    : "";

            // 🔐 ADMIN rejimga kirish
            if (text.equals("/entertoadmin")) {
                bot.awaitingAdminPassword.put(chatId, true);
                bot.executeSafely(new SendMessage(chatId.toString(), "🔑 Parolni kiriting:"));
                return true;
            }

            // 🔑 Parol tekshirish
            if (bot.awaitingAdminPassword.getOrDefault(chatId, false)) {
                if (text.equals(ADMIN_PASSWORD)) {
                    bot.awaitingAdminPassword.put(chatId, false);
                    bot.executeSafely(new SendMessage(chatId.toString(), "✅ Xush kelibsiz, admin!"));
                    bot.showAdminMenu(chatId);
                } else {
                    bot.executeSafely(new SendMessage(chatId.toString(), "❌ Noto‘g‘ri parol!"));
                }
                return true;
            }

            // 📢 Admin menyusi orqali xabar yuborish
            if (chatId.equals(ADMIN_ID) && text.equals("📢 Habar yuborish")) {
                SendMessage msg = new SendMessage(chatId.toString(), "📝 Yuboriladigan xabar turini tanlang:");
                msg.setReplyMarkup(getBroadcastTypeButtons());
                bot.executeSafely(msg);
                return true;
            }

            // 🖼 Inline tugmalardan callback kelganda
            if (update.hasCallbackQuery()) {
                String data = update.getCallbackQuery().getData();
                if (data.equals("text")) {
                    awaitingBroadcast.put(chatId, true);
                    bot.executeSafely(new SendMessage(chatId.toString(), "✏️ Matnni kiriting:"));
                    return true;
                } else if (data.equals("photo")) {
                    awaitingPhoto.put(chatId, true);
                    bot.executeSafely(new SendMessage(chatId.toString(), "🖼 Rasmni yuboring:"));
                    return true;
                }
            }

            // 🖼 Agar admin rasm yuborgan bo‘lsa
            if (awaitingPhoto.getOrDefault(chatId, false)
                    && update.hasMessage()
                    && update.getMessage().hasPhoto()) {

                List<PhotoSize> photos = update.getMessage().getPhoto();
                if (photos != null && !photos.isEmpty()) {
                    String fileId = photos.get(photos.size() - 1).getFileId();
                    photoFileId.put(chatId, fileId);
                    awaitingPhoto.put(chatId, false);
                    awaitingBroadcast.put(chatId, true);
                    bot.executeSafely(new SendMessage(chatId.toString(), "📝 Endi caption (yozuv)ni kiriting:"));
                }
                return true;
            }

            // 💬 Agar admin matn yuborayotgan bo‘lsa (rasm bilan yoki rasmsiz)
            if (awaitingBroadcast.getOrDefault(chatId, false)
                    && text != null
                    && !text.isEmpty()
                    && !text.startsWith("/")) {

                String photoId = photoFileId.get(chatId);
                ResultSet rs = db.getAllUsers();
                int sentCount = 0;

                while (rs != null && rs.next()) {
                    Long userId = rs.getLong("chat_id");

                    try {
                        if (photoId != null) {
                            SendPhoto photo = new SendPhoto();
                            photo.setChatId(userId.toString());
                            photo.setPhoto(new InputFile(photoId));
                            photo.setCaption("📢 Admin xabari:\n\n" + text);
                            bot.execute(photo);
                        } else {
                            SendMessage msg = new SendMessage(userId.toString(), "📢 Admin xabari:\n\n" + text);
                            bot.execute(msg);
                        }
                        sentCount++;

                    } catch (TelegramApiRequestException e) {
                        // ❗ Foydalanuvchi bloklagan yoki botga yozishni taqiqlagan holat
                        if (e.getMessage().contains("403") ||
                                e.getMessage().toLowerCase().contains("bot was blocked by the user")) {
                            System.out.println("⚠️ Foydalanuvchi botni bloklagan: " + userId);
                        } else {
                            System.out.println("⚠️ Yuborishda xatolik (userId=" + userId + "): " + e.getMessage());
                        }
                    } catch (Exception e) {
                        System.out.println("❌ Xabar yuborishda xatolik: " + e.getMessage());
                    }
                }

                bot.executeSafely(new SendMessage(chatId.toString(),
                        "✅ Xabar " + sentCount + " ta foydalanuvchiga yuborildi."));

                // 🔒 Holatlarni tozalaymiz
                awaitingBroadcast.put(chatId, false);
                awaitingPhoto.put(chatId, false);
                photoFileId.remove(chatId);

                bot.showAdminMenu(chatId);
                return true;
            }

            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 🔘 Inline tugmalar (matn / rasm tanlash)
    private InlineKeyboardMarkup getBroadcastTypeButtons() {
        InlineKeyboardButton textBtn = new InlineKeyboardButton("💬 Faqat matn");
        textBtn.setCallbackData("text");

        InlineKeyboardButton photoBtn = new InlineKeyboardButton("🖼 Rasm bilan");
        photoBtn.setCallbackData("photo");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Arrays.asList(textBtn, photoBtn));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    // 🆔 Chat ID olish
    private Long getChatId(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        if (update.hasCallbackQuery()) return update.getCallbackQuery().getMessage().getChatId();
        return null;
    }
}
