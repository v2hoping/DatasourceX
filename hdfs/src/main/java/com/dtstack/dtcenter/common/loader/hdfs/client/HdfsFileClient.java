package com.dtstack.dtcenter.common.loader.hdfs.client;

import com.dtstack.dtcenter.common.exception.DtCenterDefException;
import com.dtstack.dtcenter.common.hadoop.HdfsOperator;
import com.dtstack.dtcenter.common.loader.hdfs.HdfsConnFactory;
import com.dtstack.dtcenter.common.loader.hdfs.downloader.HdfsORCDownload;
import com.dtstack.dtcenter.common.loader.hdfs.downloader.HdfsParquetDownload;
import com.dtstack.dtcenter.common.loader.hdfs.downloader.HdfsTextDownload;
import com.dtstack.dtcenter.common.loader.hdfs.downloader.YarnDownload;
import com.dtstack.dtcenter.common.loader.hdfs.fileMerge.core.CombineMergeBuilder;
import com.dtstack.dtcenter.common.loader.hdfs.fileMerge.core.CombineServer;
import com.dtstack.dtcenter.common.loader.hdfs.hdfswriter.HdfsOrcWriter;
import com.dtstack.dtcenter.common.loader.hdfs.hdfswriter.HdfsParquetWriter;
import com.dtstack.dtcenter.common.loader.hdfs.hdfswriter.HdfsTextWriter;
import com.dtstack.dtcenter.common.loader.hdfs.util.KerberosUtil;
import com.dtstack.dtcenter.common.loader.hdfs.util.StringUtil;
import com.dtstack.dtcenter.loader.DtClassConsistent;
import com.dtstack.dtcenter.loader.IDownloader;
import com.dtstack.dtcenter.loader.client.IHdfsFile;
import com.dtstack.dtcenter.loader.dto.ColumnMetaDTO;
import com.dtstack.dtcenter.loader.dto.FileStatus;
import com.dtstack.dtcenter.loader.dto.HDFSContentSummary;
import com.dtstack.dtcenter.loader.dto.HdfsWriterDTO;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.source.HdfsSourceDTO;
import com.dtstack.dtcenter.loader.dto.source.ISourceDTO;
import com.dtstack.dtcenter.loader.enums.FileFormat;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.hive.ql.io.orc.OrcFile;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 14:50 2020/8/10
 * @Description：HDFS 文件操作实现类
 */
@Slf4j
public class HdfsFileClient implements IHdfsFile {

    private static final String PATH_DELIMITER = "/";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public FileStatus getStatus(ISourceDTO iSource, String location) throws Exception {
        org.apache.hadoop.fs.FileStatus hadoopFileStatus = getHadoopStatus(iSource, location);

        return FileStatus.builder()
                .length(hadoopFileStatus.getLen())
                .access_time(hadoopFileStatus.getAccessTime())
                .block_replication(hadoopFileStatus.getReplication())
                .blocksize(hadoopFileStatus.getBlockSize())
                .group(hadoopFileStatus.getGroup())
                .isdir(hadoopFileStatus.isDirectory())
                .modification_time(hadoopFileStatus.getModificationTime())
                .owner(hadoopFileStatus.getOwner())
                .path(hadoopFileStatus.getPath().toString())
                .build();
    }

    @Override
    public IDownloader getLogDownloader(ISourceDTO iSource, SqlQueryDTO queryDTO) throws Exception {
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) iSource;
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<IDownloader>) () -> {
                    try {
                        YarnDownload yarnDownload;
                        boolean containerFiledExists = Arrays.stream(HdfsSourceDTO.class.getDeclaredFields())
                                .filter(field -> "ContainerId".equalsIgnoreCase(field.getName())).findFirst().isPresent();
                        if (!containerFiledExists || StringUtils.isEmpty(hdfsSourceDTO.getContainerId())) {
                            yarnDownload = new YarnDownload(hdfsSourceDTO.getUser(), hdfsSourceDTO.getConfig(), hdfsSourceDTO.getYarnConf(), hdfsSourceDTO.getAppIdStr(), hdfsSourceDTO.getReadLimit(), hdfsSourceDTO.getLogType(), hdfsSourceDTO.getKerberosConfig());
                        } else {
                            yarnDownload = new YarnDownload(hdfsSourceDTO.getUser(), hdfsSourceDTO.getConfig(), hdfsSourceDTO.getYarnConf(), hdfsSourceDTO.getAppIdStr(), hdfsSourceDTO.getReadLimit(), hdfsSourceDTO.getLogType(), hdfsSourceDTO.getKerberosConfig(), hdfsSourceDTO.getContainerId());
                        }
                        yarnDownload.configure();
                        return yarnDownload;
                    } catch (Exception e) {
                        throw new DtCenterDefException("创建下载器异常", e);
                    }
                }
        );
    }

    /**
     * 获取 HADOOP 文件信息
     *
     * @param source
     * @param location
     * @return
     * @throws Exception
     */
    private org.apache.hadoop.fs.FileStatus getHadoopStatus(ISourceDTO source, String location) throws Exception {
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);

        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<org.apache.hadoop.fs.FileStatus>) () -> {
                    try {
                        return getFileStatus(conf, location);
                    } catch (Exception e) {
                        throw new DtCenterDefException("获取 hdfs 文件状态异常", e);
                    }
                }
        );

    }

    private org.apache.hadoop.fs.FileStatus getFileStatus (Configuration conf, String location) throws Exception{
        log.info("Hdfs get {} fileStatus;", location);
        if (HdfsOperator.isFileExist(conf, location)) {
            return HdfsOperator.getFileStatus(conf, location);
        }
        if (HdfsOperator.isDirExist(conf, location)) {
            FileSystem fs = HdfsOperator.getFileSystem(conf);
            Path path = new Path(location);
            return fs.getFileStatus(path);
        }
        throw new DtCenterDefException("路径不存在");
    }

    @Override
    public boolean downloadFileFromHdfs(ISourceDTO source, String remotePath, String localDir) throws Exception {
        log.info("Hdfs downloadFile from {} to {};", remotePath, localDir);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);

        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        HdfsOperator.downloadFileFromHDFS(conf, remotePath, localDir);
                        return true;
                    } catch (Exception e) {
                        throw new DtCenterDefException("从hdfs下载文件异常", e);
                    }
                }
        );

    }

    @Override
    public boolean uploadLocalFileToHdfs(ISourceDTO source, String localFilePath, String remotePath) throws Exception {
        log.info("Hdfs uploadFile from {} to {};", localFilePath, remotePath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);

        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        HdfsOperator.uploadLocalFileToHdfs(conf, localFilePath, remotePath);
                        return true;
                    } catch (Exception e) {
                        throw new DtCenterDefException("上传文件到hdfs异常", e);
                    }
                }
        );
    }

    @Override
    public boolean uploadInputStreamToHdfs(ISourceDTO source, byte[] bytes, String remotePath) throws Exception {
        log.info("Hdfs uploadFile to {};", remotePath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);

        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        return HdfsOperator.uploadInputStreamToHdfs(conf, bytes, remotePath);
                    } catch (Exception e) {
                        throw new DtCenterDefException("上传文件到hdfs异常", e);
                    }
                }
        );
    }

    @Override
    public boolean createDir(ISourceDTO source, String remotePath, Short permission) throws Exception {
        log.info("Hdfs createDir {};", remotePath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        return HdfsOperator.createDir(conf, remotePath, permission);
                    } catch (Exception e) {
                        throw new DtCenterDefException("在hdfs创建文件夹异常", e);
                    }
                }
        );
    }

    @Override
    public boolean isFileExist(ISourceDTO source, String remotePath) throws Exception {
        log.info("Hdfs check file exists {};", remotePath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        return HdfsOperator.isFileExist(conf, remotePath);
                    } catch (Exception e) {
                        throw new DtCenterDefException("获取文件是否存在异常", e);
                    }
                }
        );
    }

    @Override
    public boolean checkAndDelete(ISourceDTO source, String remotePath) throws Exception {
        log.info("Hdfs check file and delete {};", remotePath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        return HdfsOperator.checkAndDele(conf, remotePath);
                    } catch (Exception e) {
                        throw new DtCenterDefException("文件检测异常", e);
                    }
                }
        );
    }

    @Override
    public boolean delete(ISourceDTO source, String remotePath, boolean recursive) throws Exception {
        log.info("Hdfs delete file {};", remotePath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        Configuration conf = getHadoopConf(hdfsSourceDTO);
                        FileSystem fs = FileSystem.get(conf);
                        return fs.delete(new Path(remotePath), recursive);
                    } catch (Exception e) {
                        throw new DtLoaderException("目标路径删除异常", e);
                    }
                }
        );
    }

    @Override
    public boolean copyDirector(ISourceDTO source, String src, String dist) throws Exception {
        log.info("Hdfs copy director from {} to {};", src, dist);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        Path srcPath = new Path(src);
                        Path distPath = new Path(dist);
                        Configuration conf = getHadoopConf(hdfsSourceDTO);
                        FileSystem fs = FileSystem.get(conf);
                        if (fs.exists(srcPath)) {
                            //判断是不是文件夹
                            if (fs.isDirectory(srcPath)) {
                                if (!FileUtil.copy(fs, srcPath, fs, distPath, false, conf)) {
                                    throw new DtLoaderException("copy " + src + " to " + dist + " failed");
                                }
                            } else {
                                throw new DtLoaderException(src + "is not a directory");
                            }
                        } else {
                            throw new DtLoaderException(src + " is not exists");
                        }
                        return true;
                    } catch (Exception e) {
                        throw new DtLoaderException("目标路径删除异常", e);
                    }
                }
        );
    }

    @Override
    public boolean fileMerge(ISourceDTO source, String src, String mergePath, FileFormat fileFormat, Long maxCombinedFileSize, Long needCombineFileSizeLimit) throws Exception {
        log.info("Hdfs fileMerge from {} to {};", src, mergePath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        Configuration conf = getHadoopConf(hdfsSourceDTO);
                        CombineServer build = new CombineMergeBuilder()
                                .sourcePath(src)
                                .mergedPath(mergePath)
                                .fileType(fileFormat)
                                .maxCombinedFileSize(maxCombinedFileSize)
                                .needCombineFileSizeLimit(needCombineFileSizeLimit)
                                .configuration(conf)
                                .build();
                        build.combine();
                        return true;
                    } catch (Exception e) {
                        throw new DtLoaderException(String.format("文件合并异常：%s", e.getMessage()), e);
                    }
                }
        );
    }

    @Override
    public long getDirSize(ISourceDTO source, String remotePath) throws Exception {
        log.info("Hdfs get dir size {};", remotePath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Long>) () -> {
                    try {
                        return HdfsOperator.getDirSize(conf, remotePath);
                    } catch (Exception e) {
                        throw new DtCenterDefException("获取 hdfs 文件大小异常", e);
                    }
                }
        );
    }

    @Override
    public boolean deleteFiles(ISourceDTO source, List<String> fileNames) throws Exception {
        log.info("Hdfs delete files {};", fileNames);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        HdfsOperator.deleteFiles(conf, fileNames);
                        return true;
                    } catch (Exception e) {
                        throw new DtCenterDefException("从 hdfs 删除文件异常", e);
                    }
                }
        );
    }

    @Override
    public boolean isDirExist(ISourceDTO source, String remotePath) throws Exception {
        log.info("Hdfs check dir exist {};", remotePath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        return HdfsOperator.isDirExist(conf, remotePath);
                    } catch (Exception e) {
                        throw new DtCenterDefException("判断文件夹是否存在异常", e);
                    }
                }
        );
    }

    @Override
    public boolean setPermission(ISourceDTO source, String remotePath, String mode) throws Exception {
        log.info("Hdfs set permission {};", remotePath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        HdfsOperator.setPermission(conf, remotePath, mode);
                        return true;
                    } catch (Exception e) {
                        throw new DtCenterDefException("hdfs权限设置异常", e);
                    }
                }
        );
    }

    @Override
    public boolean rename(ISourceDTO source, String src, String dist) throws Exception {
        log.info("Hdfs rename file from {} to {};", src, dist);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        return HdfsOperator.rename(conf, src, dist);
                    } catch (Exception e) {
                        throw new DtCenterDefException("hdfs 文件重命名异常", e);
                    }
                }
        );
    }

    @Override
    public boolean copyFile(ISourceDTO source, String src, String dist, boolean isOverwrite) throws Exception {
        log.info("Hdfs copy file from {} to {};", src, dist);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        HdfsOperator.copyFile(conf, src, dist, isOverwrite);
                        return true;
                    } catch (Exception e) {
                        throw new DtCenterDefException("hdfs内 文件复制异常", e);
                    }
                }
        );
    }

    @Override
    public List<FileStatus> listStatus(ISourceDTO source, String remotePath) throws Exception {
        log.info("Hdfs list file or dir status {};", remotePath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<List<FileStatus>>) () -> {
                    try {
                        return transferFileStatus(HdfsOperator.listStatus(conf, remotePath));
                    } catch (Exception e) {
                        throw new DtCenterDefException("获取 hdfs目录 文件异常", e);
                    }
                }
        );
    }

    @Override
    public List<String> listAllFilePath(ISourceDTO source, String remotePath) throws Exception {
        log.info("Hdfs list all file path {};", remotePath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<List<String>>) () -> {
                    try {
                        return HdfsOperator.listAllFilePath(conf, remotePath);
                    } catch (Exception e) {
                        throw new DtCenterDefException("获取 hdfs目录 文件异常", e);
                    }
                }
        );
    }

    @Override
    public List<FileStatus> listAllFiles(ISourceDTO source, String remotePath, boolean isIterate) throws Exception {
        log.info("Hdfs list all files {};", remotePath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<List<FileStatus>>) () -> {
                    try {
                        return transferFileStatus(HdfsOperator.listFiles(conf, remotePath, isIterate));
                    } catch (Exception e) {
                        throw new DtCenterDefException("获取 hdfs 目录下文件状态异常", e);
                    }
                }
        );
    }

    @Override
    public boolean copyToLocal(ISourceDTO source, String srcPath, String dstPath) throws Exception {
        log.info("Hdfs copy file from {} to local {};", srcPath, dstPath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        HdfsOperator.copyToLocal(conf, srcPath, dstPath);
                        return true;
                    } catch (Exception e) {
                        throw new DtCenterDefException("copy hdfs 文件到本地异常", e);
                    }
                }
        );
    }

    @Override
    public boolean copyFromLocal(ISourceDTO source, String srcPath, String dstPath, boolean overwrite) throws Exception {
        log.info("Hdfs copy file from local {} to {};", srcPath, dstPath);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        Configuration conf = getHadoopConf(hdfsSourceDTO);
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Boolean>) () -> {
                    try {
                        HdfsOperator.copyFromLocal(conf, srcPath, dstPath, overwrite);
                        return true;
                    } catch (Exception e) {
                        throw new DtCenterDefException("从本地copy 文件到 hdfs异常", e);
                    }
                }
        );
    }

    @Override
    public IDownloader getDownloaderByFormat(ISourceDTO source, String tableLocation, String fieldDelimiter, String fileFormat) throws Exception {
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<IDownloader>) () -> {
                    try {
                        return createDownloader(hdfsSourceDTO, tableLocation, fieldDelimiter, fileFormat, hdfsSourceDTO.getKerberosConfig());
                    } catch (Exception e) {
                        throw new DtCenterDefException("创建下载器异常", e);
                    }
                }
        );

    }

    /**
     * 根据存储格式创建对应的hdfs下载器
     *
     * @param hdfsSourceDTO
     * @param tableLocation
     * @param fieldDelimiter
     * @param fileFormat
     * @return
     */
    private IDownloader createDownloader(HdfsSourceDTO hdfsSourceDTO, String tableLocation, String fieldDelimiter, String fileFormat, Map<String, Object> kerberosConfig) throws Exception {
        if (FileFormat.TEXT.getVal().equals(fileFormat)) {
            HdfsTextDownload hdfsTextDownload = new HdfsTextDownload(hdfsSourceDTO, tableLocation, null, fieldDelimiter, null, kerberosConfig);
            hdfsTextDownload.configure();
            return hdfsTextDownload;
        }

        if (FileFormat.ORC.getVal().equals(fileFormat)) {
            HdfsORCDownload hdfsORCDownload = new HdfsORCDownload(hdfsSourceDTO, tableLocation, null, null, kerberosConfig);
            hdfsORCDownload.configure();
            return hdfsORCDownload;
        }

        if (FileFormat.PARQUET.getVal().equals(fileFormat)) {
            HdfsParquetDownload hdfsParquetDownload = new HdfsParquetDownload(hdfsSourceDTO, tableLocation, null, null, kerberosConfig);
            hdfsParquetDownload.configure();
            return hdfsParquetDownload;
        }

        throw new DtCenterDefException("暂不支持该存储类型文件写入hdfs");
    }

    @Override
    public List<ColumnMetaDTO> getColumnList(ISourceDTO source, SqlQueryDTO queryDTO, String fileFormat) throws Exception {
        log.info("Hdfs get column lists by query : {}, fileFormat : {};", queryDTO, fileFormat);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<List<ColumnMetaDTO>>) () -> {
                    try {
                        return getColumnListOnFileFormat(hdfsSourceDTO, queryDTO, fileFormat);
                    } catch (Exception e) {
                        throw new DtCenterDefException("获取hdfs文件字段信息异常", e);
                    }
                }
        );

    }

    @Override
    public int writeByPos(ISourceDTO source, HdfsWriterDTO hdfsWriterDTO) throws Exception {
        log.info("Hdfs write by position {};", hdfsWriterDTO);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Integer>) () -> {
                    try {
                        return writeByPosWithFileFormat(hdfsSourceDTO, hdfsWriterDTO);
                    } catch (Exception e) {
                        throw new DtCenterDefException("获取hdfs文件字段信息异常", e);
                    }
                }
        );
    }

    @Override
    public int writeByName(ISourceDTO source, HdfsWriterDTO hdfsWriterDTO) throws Exception {
        log.info("Hdfs write by name {};", hdfsWriterDTO);
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        // kerberos认证
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<Integer>) () -> {
                    try {
                        return writeByNameWithFileFormat(hdfsSourceDTO, hdfsWriterDTO);
                    } catch (Exception e) {
                        throw new DtCenterDefException("获取hdfs文件字段信息异常", e);
                    }
                }
        );
    }

    @Override
    public HDFSContentSummary getContentSummary(ISourceDTO source, String HDFSDirPath) throws Exception {
        log.info("Hdfs get file context summary {};", HDFSDirPath);
        return getContentSummary(source, Lists.newArrayList(HDFSDirPath)).get(0);
    }

    @Override
    public List<HDFSContentSummary> getContentSummary(ISourceDTO source, List<String> HDFSDirPaths) throws Exception {
        log.info("Hdfs get file context summary {};", HDFSDirPaths);
        if (CollectionUtils.isEmpty(HDFSDirPaths)) {
            throw new DtLoaderException("hdfs路径不能为空！");
        }
        HdfsSourceDTO hdfsSourceDTO = (HdfsSourceDTO) source;
        List<HDFSContentSummary> HDFSContentSummaries = Lists.newArrayList();
        // kerberos认证
        return KerberosUtil.loginWithUGI(hdfsSourceDTO.getKerberosConfig()).doAs(
                (PrivilegedAction<List<HDFSContentSummary>>) () -> {
                    try {
                        Configuration conf = getHadoopConf(hdfsSourceDTO);
                        FileSystem fs = FileSystem.get(conf);
                        for (String HDFSDirPath : HDFSDirPaths) {
                            Path hdfsPath = new Path(HDFSDirPath);
                            // 判断路径是否存在，不存在则返回空对象
                            HDFSContentSummary hdfsContentSummary;
                            if (!fs.exists(hdfsPath)) {
                                log.warn("execute method getContentSummary: path {} not exists!", HDFSDirPath);
                                hdfsContentSummary = HDFSContentSummary.builder()
                                        .directoryCount(0L)
                                        .fileCount(0L)
                                        .ModifyTime(0L)
                                        .spaceConsumed(0L).build();

                            } else {
                                org.apache.hadoop.fs.FileStatus fileStatus = fs.getFileStatus(hdfsPath);
                                ContentSummary contentSummary = fs.getContentSummary(hdfsPath);
                                hdfsContentSummary = HDFSContentSummary.builder()
                                        .directoryCount(contentSummary.getDirectoryCount())
                                        .fileCount(contentSummary.getFileCount())
                                        .ModifyTime(fileStatus.getModificationTime())
                                        .spaceConsumed(contentSummary.getLength()).build();
                            }
                            HDFSContentSummaries.add(hdfsContentSummary);
                        }
                        return HDFSContentSummaries;
                    } catch (Exception e) {
                        throw new DtLoaderException("获取HDFS文件摘要失败！", e);
                    }
                }
        );
    }

    private int writeByPosWithFileFormat(ISourceDTO source, HdfsWriterDTO hdfsWriterDTO) throws IOException {
        if (FileFormat.ORC.getVal().equals(hdfsWriterDTO.getFileFormat())) {
            return HdfsOrcWriter.writeByPos(source, hdfsWriterDTO);
        }
        if (FileFormat.PARQUET.getVal().equals(hdfsWriterDTO.getFileFormat())) {
            return HdfsParquetWriter.writeByPos(source, hdfsWriterDTO);
        }
        if (FileFormat.TEXT.getVal().equals(hdfsWriterDTO.getFileFormat())) {
            return HdfsTextWriter.writeByPos(source, hdfsWriterDTO);
        }
        throw new DtCenterDefException("暂不支持该存储类型文件写入hdfs");
    }

    private int writeByNameWithFileFormat(ISourceDTO source, HdfsWriterDTO hdfsWriterDTO) throws IOException {
        if (FileFormat.ORC.getVal().equals(hdfsWriterDTO.getFileFormat())) {
            return HdfsOrcWriter.writeByName(source, hdfsWriterDTO);
        }
        if (FileFormat.PARQUET.getVal().equals(hdfsWriterDTO.getFileFormat())) {
            return HdfsParquetWriter.writeByName(source, hdfsWriterDTO);
        }
        if (FileFormat.TEXT.getVal().equals(hdfsWriterDTO.getFileFormat())) {
            return HdfsTextWriter.writeByName(source, hdfsWriterDTO);
        }
        throw new DtCenterDefException("暂不支持该存储类型文件写入hdfs");
    }

    private List<ColumnMetaDTO> getColumnListOnFileFormat(HdfsSourceDTO hdfsSourceDTO, SqlQueryDTO queryDTO, String fileFormat) throws IOException {

        if (FileFormat.ORC.getVal().equals(fileFormat)) {
            return getOrcColumnList(hdfsSourceDTO, queryDTO);
        }

        throw new DtCenterDefException("暂时不支持该存储类型的文件字段信息获取");
    }

    private List<ColumnMetaDTO> getOrcColumnList(HdfsSourceDTO hdfsSourceDTO, SqlQueryDTO queryDTO) throws IOException {
        ArrayList<ColumnMetaDTO> columnList = new ArrayList<>();
        Properties props = objectMapper.readValue(hdfsSourceDTO.getConfig(), Properties.class);
        Configuration conf = new HdfsOperator.HadoopConf().setConf(hdfsSourceDTO.getDefaultFS(), props);
        FileSystem fs = HdfsOperator.getFileSystem(conf);
        OrcFile.ReaderOptions readerOptions = OrcFile.readerOptions(conf);
        readerOptions.filesystem(fs);
        String fileName = hdfsSourceDTO.getDefaultFS() + PATH_DELIMITER + queryDTO.getTableName();
        fileName = handleVariable(fileName);

        Path path = new Path(fileName);
        org.apache.hadoop.hive.ql.io.orc.Reader reader = null;
        String typeStruct = null;
        if (fs.isDirectory(path)) {
            RemoteIterator<LocatedFileStatus> iterator = fs.listFiles(path, true);
            while (iterator.hasNext()) {
                org.apache.hadoop.fs.FileStatus fileStatus = iterator.next();
                if(fileStatus.isFile() && fileStatus.getLen() > 49) {
                    Path subPath = fileStatus.getPath();
                    reader = OrcFile.createReader(subPath, readerOptions);
                    typeStruct = reader.getObjectInspector().getTypeName();
                    if(StringUtils.isNotEmpty(typeStruct)) {
                        break;
                    }
                }
            }
            if(reader == null) {
                throw new DtCenterDefException("orcfile dir is empty!");
            }

        } else {
            reader = OrcFile.createReader(path, readerOptions);
            typeStruct = reader.getObjectInspector().getTypeName();
        }

        if (StringUtils.isEmpty(typeStruct)) {
            throw new DtCenterDefException("can't retrieve type struct from " + path);
        }

        int startIndex = typeStruct.indexOf("<") + 1;
        int endIndex = typeStruct.lastIndexOf(">");
        typeStruct = typeStruct.substring(startIndex, endIndex);
        List<String> cols = StringUtil.splitIgnoreQuota(typeStruct, ',');
        for (String col : cols) {
            List<String> colNameAndType = StringUtil.splitIgnoreQuota(col, ':');
            if (CollectionUtils.isEmpty(colNameAndType) || colNameAndType.size() != 2) {
                continue;
            }
            ColumnMetaDTO metaDTO = new ColumnMetaDTO();
            metaDTO.setKey(colNameAndType.get(0));
            metaDTO.setType(colNameAndType.get(1));
            columnList.add(metaDTO);
        }
        return columnList;
    }

    private static String handleVariable(String path) {
        if (path.endsWith(PATH_DELIMITER)) {
            path = path.substring(0, path.length() - 1);
        }

        int pos = path.lastIndexOf(PATH_DELIMITER);
        String file = path.substring(pos + 1, path.length());

        if(file.matches(".*\\$\\{.*\\}.*")) {
            return path.substring(0, pos);
        }

        return path;
    }

    private Configuration getHadoopConf(HdfsSourceDTO hdfsSourceDTO){

        if (StringUtils.isBlank(hdfsSourceDTO.getDefaultFS()) || !hdfsSourceDTO.getDefaultFS().matches(DtClassConsistent.HadoopConfConsistent.DEFAULT_FS_REGEX)) {
            throw new DtCenterDefException("defaultFS格式不正确");
        }
        Properties properties = HdfsConnFactory.combineHdfsConfig(hdfsSourceDTO.getConfig(), hdfsSourceDTO.getKerberosConfig());
        Configuration conf = new HdfsOperator.HadoopConf().setConf(hdfsSourceDTO.getDefaultFS(), properties);
        //不在做重复认证 主要用于 HdfsOperator.checkConnection 中有一些数栈自己的逻辑
        conf.set("hadoop.security.authorization", "false");
        conf.set("dfs.namenode.kerberos.principal.pattern", "*");
        return conf;
    }

    /**
     * Apache Status 转换
     *
     * @param fileStatuses
     * @return
     */
    private List<FileStatus> transferFileStatus(List<org.apache.hadoop.fs.FileStatus> fileStatuses) {
        List<FileStatus> fileStatusList = new ArrayList<>();
        for (org.apache.hadoop.fs.FileStatus fileStatus : fileStatuses) {
            FileStatus fileStatusTemp = FileStatus.builder()
                    .length(fileStatus.getLen())
                    .access_time(fileStatus.getAccessTime())
                    .block_replication(fileStatus.getReplication())
                    .blocksize(fileStatus.getBlockSize())
                    .group(fileStatus.getGroup())
                    .isdir(fileStatus.isDirectory())
                    .modification_time(fileStatus.getModificationTime())
                    .owner(fileStatus.getOwner())
                    .path(fileStatus.getPath().toString())
                    .build();
            fileStatusList.add(fileStatusTemp);
        }
        return fileStatusList;
    }
}
