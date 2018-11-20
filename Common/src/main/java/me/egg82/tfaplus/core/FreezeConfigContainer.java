package me.egg82.tfaplus.core;

public class FreezeConfigContainer {
    private final boolean command;
    private final boolean chat;
    private final boolean interact;
    private final boolean attack;
    private final boolean inventory;
    private final boolean drops;
    private final boolean block;
    private final boolean move;

    public FreezeConfigContainer(boolean command, boolean chat, boolean interact, boolean attack, boolean inventory, boolean drops, boolean block, boolean move) {
        this.command = command;
        this.chat = chat;
        this.interact = interact;
        this.attack = attack;
        this.inventory = inventory;
        this.drops = drops;
        this.block = block;
        this.move = move;
    }

    public boolean getCommand() { return command; }

    public boolean getChat() { return chat; }

    public boolean getInteract() { return interact; }

    public boolean getAttack() { return attack; }

    public boolean getInventory() { return inventory; }

    public boolean getDrops() { return drops; }

    public boolean getBlock() { return block; }

    public boolean getMove() { return move; }
}
