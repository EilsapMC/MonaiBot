package i.earthme.monaibot.function.commands;

import com.alibaba.fastjson2.JSONObject;
import i.earthme.monaibot.Bootstrapper;
import i.earthme.monaibot.command.ICommand;
import i.earthme.monaibot.command.ParsedCommandArgument;
import i.earthme.monaibot.function.utils.PixivRandomPictureResponse;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.events.MessageEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;

public class PixivRPICCommand implements ICommand {
    private static final Logger logger = LogManager.getLogger(PixivRPICCommand.class);

    public static void init(){
        Bootstrapper.COMMAND_REGISTRY_MANAGER.registerCommand("rpic", new PixivRPICCommand());
    }

    @Override
    public void execute(MessageEvent originalEvent, ParsedCommandArgument commandArgument) {
        try {
            final Contact feedback = this.senderFromEvent(originalEvent);

            final JSONObject queryResult = PixivRandomPictureResponse.getNewLink(0, 1);
            final String url = PixivRandomPictureResponse.getAllLinks(queryResult).findAny().get();
            final ByteArrayInputStream downloaded = new ByteArrayInputStream(PixivRandomPictureResponse.downloadFromLink(url));

            feedback.sendMessage(Contact.uploadImage(feedback, downloaded));
        }catch (Exception e){
            logger.error("Failed to get random picture", e);
        }
    }
}
