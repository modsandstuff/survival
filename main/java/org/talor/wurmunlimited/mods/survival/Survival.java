package org.talor.wurmunlimited.mods.survival;

import com.wurmonline.math.TilePos;
import com.wurmonline.server.Items;
import com.wurmonline.server.WurmCalendar;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import com.wurmonline.server.bodys.Body;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.Server;

import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Properties;


public class Survival implements WurmServerMod, Configurable, ServerStartedListener, Initable, PreInitable {

    private boolean enableTemperatureSurvival = true;
    private boolean newPlayerProtection = false;

	@Override
	public void onServerStarted() {
	}

	@Override
	public void configure(Properties properties) {
        enableTemperatureSurvival = Boolean.parseBoolean(properties.getProperty("enableTemperatureSurvival", Boolean.toString(enableTemperatureSurvival)));
        newPlayerProtection = Boolean.parseBoolean(properties.getProperty("newPlayerProtection", Boolean.toString(newPlayerProtection)));
	}

	@Override
	public void preInit() {
	}

	@Override
	public void init() {

        HookManager.getInstance().registerHook("com.wurmonline.server.players.Player", "poll", "()Z", new InvocationHandlerFactory() {

            @Override
            public InvocationHandler createInvocationHandler() {
                return new InvocationHandler() {

                    @Override
                    public Object invoke(Object object, Method method, Object[] args) throws Throwable {

                        Player p = (Player) object;

                        if (enableTemperatureSurvival && !(p.hasSpellEffect((byte) 75) && newPlayerProtection) && !p.isDead() && p.secondsPlayed % 15.0F == 0.0F) {
                            short temperatureDelta = getTemperatureDelta(p);
                            checkBodyTemp(p, true, true, temperatureDelta);
                        }

                        return method.invoke(object, args);
                    }
                };
            }
        });

        HookManager.getInstance().registerHook("com.wurmonline.server.players.Player", "setDeathEffects", "(ZII)V", new InvocationHandlerFactory() {

            @Override
            public InvocationHandler createInvocationHandler() {
                return new InvocationHandler() {

                    @Override
                    public Object invoke(Object object, Method method, Object[] args) throws Throwable {

                        Player p = (Player) object;

                        Body b = p.getBody();

                        for (int x = 0; x < b.getSpaces().length; x++) {
                            if (b.getSpaces()[x] != null) {
                                Item[] itemarr = b.getSpaces()[x].getAllItems(false);
                                for (int y = 0; y < itemarr.length; y++) {
                                    if (itemarr[y].isBodyPart()) {
                                        itemarr[y].setTemperature((short) 200);
                                    }
                                }
                            }
                        }

                        return method.invoke(object, args);
                    }
                };
            }
        });


        HookManager.getInstance().registerHook("com.wurmonline.server.creatures.Communicator", "sendSafeServerMessage", "(Ljava/lang/String;)V", new InvocationHandlerFactory() {

            @Override
            public InvocationHandler createInvocationHandler() {
                return new InvocationHandler() {

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String HoldSentText = (String) args[0];
                        if (HoldSentText.startsWith("Unknown command: /mytemp")) {
                            return null;
                        }
                        return method.invoke(proxy, args);
                    }
                };
            }

        });

        HookManager.getInstance().registerHook("com.wurmonline.server.creatures.Communicator", "reallyHandle_CMD_MESSAGE", "(Ljava/nio/ByteBuffer;)V", new InvocationHandlerFactory() {

            @Override
            public InvocationHandler createInvocationHandler() {
                return new InvocationHandler() {

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        method.invoke(proxy, args);
                        Communicator ComObject = (Communicator) proxy;
                        String Message = ComObject.getCommandMessage();
                        Player PlayerObject = ComObject.player;
                        if (Message.charAt(0) == '/') {
                            if (enableTemperatureSurvival) {
                                if (Message.startsWith("/mytemp")) {
                                    short temperatureDelta = getTemperatureDelta(PlayerObject);
                                    short averageTemperature = checkBodyTemp(PlayerObject, false, false, temperatureDelta);
                                    String message = "";

                                    if (averageTemperature == 0) {
                                        message = message + "You are freezing cold,";
                                    } else if (averageTemperature < 50) {
                                        message = message + "You are very cold,";
                                    } else if (averageTemperature < 100) {
                                        message = message + "You are cold,";
                                    } else if (averageTemperature < 150) {
                                        message = message + "You are warm,";
                                    } else {
                                        message = message + "You are very warm,";
                                    }

                                    if (temperatureDelta == 0 || (averageTemperature < 100 && temperatureDelta < 0) || (averageTemperature >= 100 && temperatureDelta > 0)) {
                                        message = message + " and ";
                                    } else {
                                        message = message + " but ";
                                    }

                                    if (temperatureDelta < -3) {
                                        message = message + "you are rapidly getting colder.";
                                    } else if (temperatureDelta < 0) {
                                        message = message + "you are getting colder.";
                                    } else if (temperatureDelta == 0) {
                                        message = message + "this is unlikely to change.";
                                    } else if (temperatureDelta <= 3) {
                                        message = message + "you are getting warmer.";
                                    } else {
                                        message = message + "you are rapidly getting warmer.";
                                    }

                                    PlayerObject.getCommunicator().sendNormalServerMessage(message);

                                }
                            }
                        }
                        return null;
                    }
                };
            }
        });
    }


    public short checkBodyTemp(Player p, boolean applyWounds, boolean warningMessages, short temperatureDelta) {

        try {

            boolean urgentAlert = false;
            short totalTemperature = 0;
            short countBodyParts = 0;
            String message = null;
            Body b = p.getBody();

            for (int x = 0; x < b.getSpaces().length; x++) {
                if (b.getSpaces()[x] != null) {
                    Item[] itemarr = b.getSpaces()[x].getAllItems(false);

                    for (int y = 0; y < itemarr.length; y++) {
                        if (itemarr[y].isBodyPart()) {
                            short temperature = itemarr[y].getTemperature();
                            temperature = (short) Math.min(2500, Math.max(0, (int) temperature + (int) temperatureDelta));
                            itemarr[y].setTemperature(temperature);
                            totalTemperature+= temperature;
                            countBodyParts++;

                            if (temperatureDelta < 0) {
                                if (warningMessages && itemarr[y].getTemperature() < 50) {
                                    if (message == null) {
                                        message = "You are very cold and should find warmth";
                                        urgentAlert = true;
                                    }
                                }
                            } else {
                                //itemarr[y].setTemperature(temperature);
                                if (itemarr[y].getTemperature() < 50) {
                                    message = "You are warming up.";
                                }
                            }

                            if (applyWounds && temperature == 0) {
                                if (Server.rand.nextInt(1000) > 750) {

                                    byte woundPos = (short) 0;

                                    switch (itemarr[y].getName()) {

                                        case "body":
                                            woundPos = b.getCenterWoundPos();
                                            break;
                                        case "head":
                                            woundPos = b.getRandomWoundPos((byte) 7);
                                            break;
                                        case "left foot":
                                            woundPos = b.getRandomWoundPos((byte) 4);
                                            break;
                                        case "right foot":
                                            woundPos = b.getRandomWoundPos((byte) 3);
                                            break;
                                        case "right arm":
                                            woundPos = b.getRandomWoundPos((byte) 2);
                                            break;
                                        case "left arm":
                                            woundPos = b.getRandomWoundPos((byte) 5);
                                            break;
                                        case "right hand":
                                            woundPos = b.getRandomWoundPos((byte) 2);
                                            break;
                                        case "left hand":
                                            woundPos = b.getRandomWoundPos((byte) 5);
                                            break;
                                        case "back":
                                            woundPos = b.getCenterWoundPos();
                                            break;
                                        case "face":
                                            woundPos = b.getRandomWoundPos((byte) 7);
                                            break;
                                        case "legs":
                                            woundPos = b.getRandomWoundPos((byte) 10);
                                            break;
                                    }
                                    if (woundPos != (short) 0) {
                                        int dmg = Server.rand.nextInt(2000);
                                        if (p.secondsPlayed % 30.0F == 0.0F) {
                                            p.addWoundOfType(null, (byte) 8, woundPos, false, 1.0F, true, dmg);
                                        }
                                       if (warningMessages) {
                                           message = "You are freezing cold! Find warmth quickly.";
                                           urgentAlert = true;
                                       }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (urgentAlert && message != null) {
                p.getCommunicator().sendNormalServerMessage(message, (byte)4);
            } else if (message != null) {
                p.getCommunicator().sendNormalServerMessage(message);
            }
            return (short) (totalTemperature/countBodyParts);
        } catch (Exception e) {
            return 0;
        }
    }


    public short getTemperatureDelta(Player p) {
        try {
            int hour = WurmCalendar.getHour();
            double starfall = WurmCalendar.getStarfall() + ((double) WurmCalendar.getDay() / 28);
            boolean isIndoors = !p.getCurrentTile().isOnSurface() || (p.getCurrentTile().getStructure() != null && p.getCurrentTile().getStructure().isFinished());
            boolean isOnBoat = p.getVehicle() != (long)-10 && Items.getItem(p.getVehicle()).isBoat();

            // Approximation of seasonal heat differences
            // Produces number between -4 and 3
            double monthTempMod = 7 * Math.sin(starfall / 3.82) - 4;

            // Approximation of day/night heat differences
            // Produces number between -2 and 2
            double hourTempMod = 4 * Math.sin((float) hour / 7.65) - 2;

            // Colder if strong wind or gale
            double windMod = !isIndoors && Math.abs(Server.getWeather().getWindPower()) > 0.3 ? -1 : 0;

            // Colder if swimming
            double swimMod = !isOnBoat && Zones.calculateHeight(p.getPosX(), p.getPosY(), p.isOnSurface()) < 0 ? -2 : 0;

            // Colder if raining
            double rainMod = !isIndoors && Server.getWeather().getRain() > 0.5 ? -1 : 0;

            // Positive value indicates warming, negative value indicates cooling
            // Produces within a rough range of -10 to 5
            short temperatureDelta = (short) (monthTempMod + hourTempMod + windMod + swimMod + rainMod);

            System.out.println(p.getName() + " has following modifiers... calendar mod: " + monthTempMod + ", day/night mod: " + hourTempMod + ", windMod : " + windMod + ", swimMod: " + swimMod + ", rainMod: " + rainMod + ", indoors: " + isIndoors);

            // Search nearby for hottest heat source
            int tileX = p.getCurrentTile().getTileX();
            int tileY = p.getCurrentTile().getTileY();
            int yy;
            int dist = 5; // area to check for heat sources
            int x1 = Zones.safeTileX(tileX - dist);
            int x2 = Zones.safeTileX(tileX + dist);
            int y1 = Zones.safeTileY(tileY - dist);
            int y2 = Zones.safeTileY(tileY + dist);

            short targetTemperature = 0;

            for (TilePos tPos : TilePos.areaIterator(x1, y1, x2, y2)) {
                int xx = tPos.x;
                yy = tPos.y;
                VolaTile t = Zones.getTileOrNull(xx, yy, p.isOnSurface());
                if ((t != null)) {
                    for (Item item : t.getItems()) {
                        short effectiveTemperature = 0;
                        if (item.isOnFire()) {
                            effectiveTemperature = (short) (item.getTemperature() / Math.max(1, Math.sqrt(Math.pow(Math.abs(tileX - xx), 2) + Math.pow(Math.abs(tileY - yy), 2))));
                        }
                        if (effectiveTemperature > targetTemperature) {
                            targetTemperature = effectiveTemperature;
                        }
                    }
                }
            }

            // Add warming effect from heat source
            temperatureDelta += (short) Math.ceil((double) targetTemperature / 4000);

            return temperatureDelta;
        } catch (Exception e) {
            return 0;
        }
    }
}
