#!/bin/sh

url=$1

wget --no-proxy --no-check-certificate "$url"/icat/ICAT?wsdl -O ICAT.wsdl
rc=$?

if [ $rc -eq 0 ]; then
	sed s#"$url"/ICATService/ICAT?xsd=1#ICAT.xsd# ICAT.wsdl > ICAT.wsdl.new && mv ICAT.wsdl.new ICAT.wsdl
else
	wget --no-proxy --no-check-certificate "$url"/ICATService/ICAT?wsdl -O ICAT.wsdl
	wget --no-proxy --no-check-certificate "$url"/ICATService/ICAT?xsd=1 -O ICAT.xsd

	sed s#"$url"/ICATService/ICAT?xsd=1#ICAT.xsd# ICAT.wsdl > ICAT.wsdl.new && mv ICAT.wsdl.new ICAT.wsdl
	sed 's#ref="tns:\([a-z]*\)"#name="\1" type="tns:\1"#' ICAT.xsd > ICAT.xsd.new && mv ICAT.xsd.new ICAT.xsd
fi

