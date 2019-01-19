import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Core extends ListenerAdapter {

    private static JDA jda;

    public static void main(String[] args) throws LoginException {
        jda = new JDABuilder(AccountType.BOT).setToken("NTM0ODM4MjIxMDkyMTU5NDg5.Dx_apQ.05sZdNnU9FVnyNQfWiVNkAdzkEk").build();
        jda.addEventListener(new Core());

        jda.getPresence().setActivity(Activity.playing("Crie Micheli pour m'utiliser!"));
    }

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    private Core() {
        this.musicManagers = new HashMap<>();

        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.isFromType(ChannelType.TEXT)) {

            if (event.getAuthor().isBot()) return;

            String message = event.getMessage().getContentDisplay();
            TextChannel channel = event.getTextChannel();
            Member member = event.getMember();

            String[] params = message.split(" ");

            if(params.length > 0 && params[0].equalsIgnoreCase("micheli")){

                if(params.length == 1){
                    member.getUser().openPrivateChannel().queue((chan) ->
                    {
                        chan.sendMessage("Que puis-je pour vous ?").queue();

                        chan.sendMessage("```"
                        +"joue [url] -> Ecoute ta musique préféré"
                        +"\nchut     -> Si t'en as marre de m'entendre"
                        +"\nsuivant  -> Musique suivante"
                        +"\nrepete   -> Parce qu'un bon son ça s'écoute plus d'une fois"
                        +"\nmelange  -> Musiques triés aléatoirement"
                        +"\nmiam     -> Repas du jour"
                        +"\nattaque  -> Trashtalk une personne aléatoire"
                        +"\nvolume   -> Change le volume```").queue();
                    });
                }
                //if(member.getUser().getId().equals("225324061968957441")){
                  //  channel.sendMessage("https://media1.tenor.com/images/39e4874facfc3720dde08ce47e04d4f8/tenor.gif?itemid=4092740").queue();
                // }

                if(params.length > 1){
                   if(params[1].equalsIgnoreCase("chut")){
                       stopTrack(channel);
                   }else if(params[1].equalsIgnoreCase("suivant")){
                       skipTrack(channel);
                   }else if(params[1].equalsIgnoreCase("repete")){
                       repeatMode(channel);
                   }else if(params[1].equalsIgnoreCase("melange")){
                       shuffleMode(channel);
                   }else if(params[1].equalsIgnoreCase("miam")){
                       afficheMenuRU(jda.getTextChannelById("535058047652069416"));
                   }else if(params[1].equalsIgnoreCase("attaque")){
                       int r = new Random().nextInt(channel.getMembers().size());

                       channel.sendMessage(channel.getMembers().get(r).getAsMention()+" petit merdeux, viens par là !").queue();
                   }
                }

                if(params.length > 2){
                    if(params[1].equalsIgnoreCase("joue")){
                        loadAndPlay(channel, params[2]);
                    }else if(params[1].equalsIgnoreCase("volume")){
                        changeVolume(channel, params[2]);
                    }
                }
            }
        }
    }

    private void loadAndPlay(final TextChannel channel, final String trackUrl) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                channel.sendMessage("Ca marche, j'ajoute " + track.getInfo().title).queue();

                play(channel.getGuild(), musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();

                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                channel.sendMessage("Ca marche, j'ajoute " + firstTrack.getInfo().title + " (1er son de la playlist " + playlist.getName() + ")").queue();

                play(channel.getGuild(), musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
                channel.sendMessage("Ca existe pas, j'ai bien l'impression" + trackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage("J'arrive pas à jouer :( " + exception.getMessage()).queue();
            }
        });
    }

    private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track) {
        connectToFirstVoiceChannel(guild.getAudioManager());

        musicManager.scheduler.queue(track);
    }

    private void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.scheduler.nextTrack();

        channel.sendMessage("Que la fête continue !").queue();
    }

    private void stopTrack(TextChannel channel){
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.player.stopTrack();

        channel.sendMessage("A votre service !").queue();
    }

    private void changeVolume(TextChannel channel, String input){
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        AudioPlayer player = musicManager.player;

        int newVolume = Math.max(10, Math.min(100, Integer.parseInt(input)));
        int oldVolume = player.getVolume();
        player.setVolume(newVolume);
        channel.sendMessage("Volume changé de `" + oldVolume + "` à `" + newVolume + "`").queue();
    }

    private void repeatMode(TextChannel channel){
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        musicManager.scheduler.setRepeating(!musicManager.scheduler.isRepeating());

        channel.sendMessage("Mode répétition des sons activé !").queue();
    }

    private void shuffleMode(TextChannel channel){
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        if (musicManager.scheduler.queue.isEmpty())
        {
            channel.sendMessage("Il n'y a rien à jouer!").queue();
            return;
        }

        musicManager.scheduler.shuffle();
        channel.sendMessage("Mélange des sons effectué !").queue();
    }

    private static void connectToFirstVoiceChannel(AudioManager audioManager) {
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
                audioManager.openAudioConnection(voiceChannel);
                break;
            }
        }
    }

    private void afficheMenuRU(TextChannel channel){
        String[] listeRU = new String[]{"http://www.crous-lille.fr/restaurant/r-u-sully-lille-i/", "http://www.crous-lille.fr/restaurant/r-u-pariselle-lille-i/", "http://www.crous-lille.fr/restaurant/r-u-barrois-lille-i/"};
        String finale = "";

        finale += "Voici les plats du jour ! (Sully, Pariselle, Barrois) \n";

        for(String ru : listeRU){
            Document doc = null;

            try{
                doc = Jsoup.connect(ru).get();
            }catch(IOException e){};

            Elements plats = doc.select("#menu-repas").select("li").get(0).select(".content-repas").get(1).select("ul");

            finale += "\n```";

            for(int i=0; i < plats.size(); i++){
                finale += "\n"+plats.get(i).previousElementSibling().text()+":";
                finale += "\n  "+plats.get(i).text();
            }

            if(plats.size() == 0){
                finale+="Aucun plat disponible";
            }

            finale += "\n```";

        }

        channel.sendMessage(finale).queue();
    }

}
