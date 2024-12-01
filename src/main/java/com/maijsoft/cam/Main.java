/*
 * Copyright (C) 2024 MAIJSOFT Dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.maijsoft.cam;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main extends JavaPlugin implements TabCompleter {
    private final Map<Integer, Location> startPositions = new HashMap<>();
    private final Map<Integer, Location> endPositions = new HashMap<>();
    private final Map<Integer, Double> durations = new HashMap<>();
    private final Map<Integer, Location> endTeleportPositions = new HashMap<>();
    private final Map<Player, GameMode> originalGameModes = new HashMap<>();

    @Override
    public void onEnable() {
        getCommand("cm").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("Invalid usage. Try /cm <command> <number> [options]");
                return true;
            }

            String subCommand = args[0].toLowerCase();
            int actionNumber;

            try {
                actionNumber = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("Action number must be an integer.");
                return true;
            }

            switch (subCommand) {
                case "startpos" -> {
                    startPositions.put(actionNumber, player.getLocation());
                    player.sendMessage("Start position for action " + actionNumber + " set.");
                }
                case "endpos" -> {
                    endPositions.put(actionNumber, player.getLocation());
                    player.sendMessage("End position for action " + actionNumber + " set.");
                }
                case "endtp" -> {
                    endTeleportPositions.put(actionNumber, player.getLocation());
                    player.sendMessage("End teleport position for action " + actionNumber + " set.");
                }
                case "postime" -> {
                    if (args.length < 3) {
                        player.sendMessage("Please specify the duration in seconds.");
                        return true;
                    }
                    try {
                        double duration = Double.parseDouble(args[2]);
                        durations.put(actionNumber, duration);
                        player.sendMessage("Duration for action " + actionNumber + " set to " + duration + " seconds.");
                    } catch (NumberFormatException e) {
                        player.sendMessage("Duration must be a valid number.");
                    }
                }
                case "start" -> {
                    if (args.length < 3) {
                        player.sendMessage("Please specify a movement function.");
                        return true;
                    }

                    String movementFunction = args[2].toLowerCase();
                    if (!List.of("linear", "easein", "easeout", "easeinout").contains(movementFunction)) {
                        player.sendMessage("Invalid movement function. Choose from: linear, easein, easeout, easeinout.");
                        return true;
                    }

                    if (!startPositions.containsKey(actionNumber) || !endPositions.containsKey(actionNumber) || !durations.containsKey(actionNumber)) {
                        player.sendMessage("Action " + actionNumber + " is not fully configured.");
                        return true;
                    }
                    startCamera(player, actionNumber, movementFunction);
                }
                default -> player.sendMessage("Unknown subcommand: " + subCommand);
            }

            return true;
        });

        getCommand("cm").setTabCompleter(this);
    }

    private void startCamera(Player player, int actionNumber, String movementFunction) {
        Location start = startPositions.get(actionNumber);
        Location end = endPositions.get(actionNumber);
        double duration = durations.get(actionNumber);
        Location endTeleport = endTeleportPositions.getOrDefault(actionNumber, end);

        // 원래 게임 모드 저장
        originalGameModes.put(player, player.getGameMode());
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(start);

        new BukkitRunnable() {
            double t = 0;
            final double step = 1.0 / (duration * 20); // 매 틱(tick)마다 진행 비율(step)

            @Override
            public void run() {
                t += step;

                if (t >= 1) {
                    // 카메라 종료 처리
                    player.teleport(endTeleport);
                    player.sendMessage("Camera movement completed.");

                    GameMode originalGameMode = originalGameModes.remove(player);
                    if (originalGameMode != null) {
                        player.setGameMode(originalGameMode);
                    }

                    cancel();
                    return;
                }

                // 선택된 움직임 함수에 따른 진행 비율 계산
                double adjustedT = switch (movementFunction) {
                    case "easein" -> easeIn(t);
                    case "easeout" -> easeOut(t);
                    case "easeinout" -> easeInOutCubic(t);
                    default -> t; // Linear
                };

                double x = interpolate(start.getX(), end.getX(), adjustedT);
                double y = interpolate(start.getY(), end.getY(), adjustedT);
                double z = interpolate(start.getZ(), end.getZ(), adjustedT);
                float yaw = (float) interpolate(start.getYaw(), end.getYaw(), adjustedT);
                float pitch = (float) interpolate(start.getPitch(), end.getPitch(), adjustedT);

                player.teleport(new Location(start.getWorld(), x, y, z, yaw, pitch));
            }
        }.runTaskTimer(this, 0, 1); // 1틱 간격으로 실행
    }

    private double interpolate(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private double easeIn(double t) {
        return t * t;
    }

    private double easeOut(double t) {
        return 1 - Math.pow(1 - t, 2);
    }

    private double easeInOutCubic(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("cm")) {
            if (args.length == 1) {
                List<String> subCommands = List.of("startpos", "endpos", "postime", "start", "endtp");
                return filterResults(args[0], subCommands);
            } else if (args.length == 2) {
                List<String> actionNumbers = new ArrayList<>();
                for (int key : startPositions.keySet()) {
                    actionNumbers.add(String.valueOf(key));
                }
                return filterResults(args[1], actionNumbers);
            } else if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
                return filterResults(args[2], List.of("linear", "easein", "easeout", "easeinout"));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("postime")) {
                return List.of("1.0", "5.0", "10.0");
            }
        }
        return null;
    }

    private List<String> filterResults(String input, List<String> options) {
        List<String> results = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(input.toLowerCase())) {
                results.add(option);
            }
        }
        return results;
    }
}
