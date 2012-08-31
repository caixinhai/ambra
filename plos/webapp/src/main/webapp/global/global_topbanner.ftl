<#-- depending on the current page, set banner zones -->

<#assign topRight = 35>

<#if pgURL?contains('browse.action')>
	<#if pgURL?contains('field=date')>
		<#assign topRight = 176>
	<#else>
		<#assign topRight = 208>
	</#if>
<#elseif pgURL?contains('browseIssue.action') || pgURL?contains('browseVolume.action')>
	<#assign topRight = 175>
<#elseif pgURL?contains('advancedSearch.action') || pgURL?contains('simpleSearch.action')>
	<#assign topRight = 211>
<#elseif pgURL?contains('article')>
	<#assign topRight = 40>
</#if>

<!-- begin : left banner slot -->
<div class="left">
<script src="http://comparenetworks.com/partnerAds/ad.aspx?adid=0&amp;adtypeid=2&amp;siteid=35&amp;subsiteid=0&amp;pagetypeid=0&amp;pageid=0&amp;subpageid=0" type="text/javascript"></script>
</div>
<!-- end : left banner slot -->
<!-- begin : right banner slot -->
<div class="right">
<script language='JavaScript' type='text/javascript'>
<!--
   if (!document.phpAds_used) document.phpAds_used = ',';
   phpAds_random = new String (Math.random()); phpAds_random = phpAds_random.substring(2,11);
   
   document.write ("<" + "script language='JavaScript' type='text/javascript' src='");
   document.write ("http://ads.plos.org/adjs.php?n=" + phpAds_random);
   document.write ("&amp;what=zone:${topRight}");
   document.write ("&amp;exclude=" + document.phpAds_used);
   if (document.referrer)
      document.write ("&amp;referer=" + escape(document.referrer));
   document.write ("'><" + "/script>");
//-->
</script><noscript><a href='http://ads.plos.org/adclick.php?n=acfd0f5a' target='_blank'><img src='http://ads.plos.org/adview.php?what=zone:${topRight}&amp;n=acfd0f5a' border='0' alt='' /></a></noscript>	
</div>
<!-- end : right banner slot -->