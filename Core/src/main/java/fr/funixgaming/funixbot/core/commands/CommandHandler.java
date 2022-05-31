package fr.funixgaming.funixbot.core.commands;

import feign.FeignException;
import fr.funixgaming.api.client.funixbot.clients.FunixBotCommandClient;
import fr.funixgaming.api.client.funixbot.dtos.FunixBotCommandDTO;
import fr.funixgaming.funixbot.core.Bot;
import fr.funixgaming.funixbot.core.commands.entities.BotCommand;
import fr.funixgaming.twitch.api.chatbot_irc.entities.ChatMember;
import fr.funixgaming.twitch.api.tools.TwitchThreadPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandHandler {
    private final Set<BotCommand> listeners = new HashSet<>();
    private final TwitchThreadPool threadPool = new TwitchThreadPool(4);

    private final FunixBotCommandClient funixBotCommandClient;

    public void addListener(final BotCommand listener) {
        listeners.add(listener);
    }

    public void onNewChat(final ChatMember member, final String message, final Bot bot, final String channelSendMessage) {
        if (message.startsWith("!")) {
            threadPool.newTask(() -> {
                final String[] args = message.split(" ");

                if (args.length > 0 && args[0].length() > 1) {
                    final String commandName = args[0].substring(1).toLowerCase();

                    for (final BotCommand command : listeners) {
                        if (isUserEnteredCommand(commandName, command)) {
                            command.onUserCommand(member, commandName, Arrays.copyOfRange(args, 1, args.length));
                            return;
                        }
                    }

                    try {
                        final List<FunixBotCommandDTO> search = this.funixBotCommandClient.search(String.format("command:%s", commandName), "0", "1");
                        if (!search.isEmpty()) {
                            final FunixBotCommandDTO commandApi = search.get(0);
                            bot.sendChatMessage(channelSendMessage, commandApi.getMessage());
                        }
                    } catch (FeignException e) {
                        log.error("Une erreur est survenue lors de la recherche de la commande ({}) twitch sur la funix api. Erreur code: {} msg: {}", commandName, e.status(), e.contentUTF8());
                    }
                }
            });
        }
    }

    private boolean isUserEnteredCommand(final String userCommand, final BotCommand command) {
        if (userCommand.equals(command.getCommandName())) {
            return true;
        }

        for (final String alias : command.getAliases()) {
            if (userCommand.equals(alias)) {
                return true;
            }
        }
        return false;
    }
}
