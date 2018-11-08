<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">

	<xsl:output method="html" html-version="5.0" encoding="UTF-8" indent="yes" doctype-system="about:legacy-compat" />

	<xsl:template match="listing">
		<html>
			<head>
				<meta name="robots" content="NOINDEX,NOFOLLOW" />
				<title>
					<xsl:choose>
						<xsl:when test="@directory = 'connectors'">Installed connectors</xsl:when>
						<xsl:otherwise>Error</xsl:otherwise>
					</xsl:choose>
				</title>
				<link rel="shortcut icon" href="/documentation/img/favicon.ico" type="image/x-icon" />
				<link rel="stylesheet" href="/documentation/css/help.css" type="text/css" />
			</head>
			<body>
				<div id="header" >
					<div class="HeaderImgLeft" ></div>
					<div class="HeaderImgRight" ></div>
					<div class="HeaderText" >
						<xsl:choose>
							<xsl:when test="@directory = 'connectors'">Installed connectors</xsl:when>
							<xsl:otherwise>Error</xsl:otherwise>
						</xsl:choose>
					</div>
				</div>
				<div id="body" class="tac" >
					<div style="text-align: left">
						<div class="Nav" ><span class="NavLink" ><a href="/documentation/index.html" >Documentation Home</a></span><span class="NavSeparator" >&gt;</span><span class="NavSelected" >Installed connectors</span></div>
						<xsl:if test="@directory = 'connectors'">
							<div class="Menu1" >Contents</div>
							<div class="Menu1Text" >
								<xsl:for-each select="entries/entry[@type='dir']">
									<div class="TOC1" ><a href="{concat(@urlPath, 'index.html')}" ><xsl:value-of select="substring(./text(), 1, string-length(./text()) - 1)"/></a></div>
								</xsl:for-each>
							</div>
						</xsl:if>
					</div>
				</div>
				<div id="footer" class="tac" >
					<div class="copy" >Copyright &#169; 2013 Sword Group. All Rights Reserved.</div>
				</div>
			</body>
		</html>
	</xsl:template>

</xsl:stylesheet>