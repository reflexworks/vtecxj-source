package jp.reflexworks.pdf.signature;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.tsp.TimeStampToken;

public class TimestampSignatureImpl implements SignatureInterface {
    private TSAClient tsaClient;
    public TimestampSignatureImpl(TSAClient tsaClient) {
        super();
        this.tsaClient = tsaClient;
    }
    @Override
    public byte[] sign(InputStream paramInputStream) throws IOException {
    	TimeStampToken timestampToken = tsaClient.getTimeStampToken(
    			IOUtils.toByteArray(paramInputStream));
    	return timestampToken.getEncoded();
    }
}
