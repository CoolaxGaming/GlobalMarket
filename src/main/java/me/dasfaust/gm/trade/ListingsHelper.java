package me.dasfaust.gm.trade;

import java.math.BigDecimal;
import java.util.UUID;

import org.bukkit.OfflinePlayer;

import me.dasfaust.gm.Core;
import me.dasfaust.gm.StorageHelper;
import me.dasfaust.gm.config.Config.Defaults;
import me.dasfaust.gm.menus.MarketViewer;
import me.dasfaust.gm.menus.Menus;
import me.dasfaust.gm.tools.LocaleHandler;

public class ListingsHelper
{
	public static void buy(MarketListing listing, UUID buyer) throws TransactionException
	{
		OfflinePlayer player = Core.instance.getServer().getOfflinePlayer(buyer);
		OfflinePlayer seller = Core.instance.getServer().getOfflinePlayer(listing.seller);
		if (!Core.instance.econ().has(player, listing.price))
		{
			throw new TransactionException(LocaleHandler.get().get("general_no_money"));
		}
		if (Core.instance.config().get(Defaults.DISABLE_STOCK))
		{
			if (!player.isOnline())
			{
				throw new TransactionException("Buyer is offline");
			}
		}
		else
		{
			if (StorageHelper.isStockFull(buyer))
			{
				throw new TransactionException(LocaleHandler.get().get("general_full_stock"));
			}
		}
		StockedItem buyerStock = null;
		StockedItem stock = null;
		if (!Core.instance.config().get(Defaults.DISABLE_STOCK))
		{
			stock = StorageHelper.stockFor(listing.seller, listing.itemId);
			if (stock == null
					|| !Core.instance.storage().verify(MarketListing.class, listing.id)
					|| !Core.instance.storage().verify(StockedItem.class, stock.id))
			{
				throw new TransactionException(LocaleHandler.get().get("general_no_stock"));
			}
			if (stock.amount < listing.amount)
			{
				throw new TransactionException(LocaleHandler.get().get("general_no_stock"));
			}
			buyerStock = StorageHelper.stockFor(buyer, listing.itemId);
			if (buyerStock != null)
			{
				if (buyerStock.amount + listing.amount > Core.instance.config().get(Defaults.STOCK_SLOTS_SIZE))
				{
					throw new TransactionException(LocaleHandler.get().get("general_full_stock"));
				}
			}
		}
		if (!Core.instance.econ().withdrawPlayer(player, listing.price).transactionSuccess())
		{
			throw new TransactionException(LocaleHandler.get().get("general_bad_econ_response"));
		}
		double cutPercentage = Core.instance.config().get(Defaults.LISTINGS_CUT_AMOUNT);
		if (cutPercentage > 0)
		{
			listing.price = listing.price - listing.price * cutPercentage;
		}
		if (!Core.instance.econ().depositPlayer(seller, round(listing.price)).transactionSuccess())
		{
			throw new TransactionException(LocaleHandler.get().get("general_bad_econ_response"));
		}
		if (!Core.instance.config().get(Defaults.DISABLE_STOCK))
		{
			if (buyerStock == null)
			{
				StockedItem _stock = new StockedItem();
				_stock.amount = listing.amount;
				_stock.creationTime = System.currentTimeMillis();
				_stock.itemId = listing.itemId;
				_stock.owner = buyer;
				// TODO: don't do this
				_stock.world = UUID.randomUUID();
				Core.instance.storage().store(_stock);
			}
			else
			{
				StorageHelper.updateStockAmount(buyerStock, buyerStock.amount + listing.amount);
			}
			if (stock.amount > listing.amount)
			{
				StorageHelper.updateStockAmount(stock, stock.amount - listing.amount);
			}
			else
			{
				Core.instance.storage().removeObject(StockedItem.class, stock.id);
				Core.instance.storage().removeObject(MarketListing.class, listing.id);
			}
		}
		else
		{
			Core.instance.storage().removeObject(MarketListing.class, listing.id);
		}
		Core.instance.handler().rebuildAllMenus(Menus.MENU_LISTINGS);
		MarketViewer viewer = Core.instance.handler().getViewer(listing.seller);
		if (viewer != null)
		{
			viewer.reset().buildMenu();
		}
	}
	
	public static double round(double a)
	{
		return new BigDecimal(a).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
	}
	
	@SuppressWarnings("serial")
	public static class TransactionException extends Exception
	{
		public TransactionException(String message) {
			super(message);
		}	
	}
	
	public static void createUnrestrictedListing(UUID seller, UUID world, WrappedStack stack, int amount, double price)
	{
		MarketListing listing = new MarketListing();
		listing.seller = seller;
		listing.world = world;
		listing.itemId = Core.instance.storage().store(stack);
		listing.amount = amount;
		listing.price = price;
		listing.creationTime = System.currentTimeMillis();
		Core.instance.storage().store(listing);
	}
	
	public static void createListing(UUID seller, UUID world, WrappedStack stack, int amount, double price) throws TransactionException
	{
		
	}
}